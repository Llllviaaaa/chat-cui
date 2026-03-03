import { randomUUID, createHash } from "node:crypto";
import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname } from "node:path";
import {
  CredentialError,
  CredentialErrorReason
} from "./CredentialErrors";
import type { CredentialProvider } from "./CredentialProvider";

interface SecretStore {
  read(secretRef: string): string | null;
  write(secretRef: string, encryptedSecret: string): void;
  delete(secretRef: string): void;
  keys(): string[];
}

export interface SecretProtector {
  protect(plaintext: string): string;
  unprotect(ciphertext: string): string;
}

class JsonFileStore implements SecretStore {
  constructor(private readonly filePath: string) {}

  read(secretRef: string): string | null {
    const data = this.load();
    return data[secretRef] ?? null;
  }

  write(secretRef: string, encryptedSecret: string): void {
    const data = this.load();
    data[secretRef] = encryptedSecret;
    this.persist(data);
  }

  delete(secretRef: string): void {
    const data = this.load();
    delete data[secretRef];
    this.persist(data);
  }

  keys(): string[] {
    return Object.keys(this.load());
  }

  private load(): Record<string, string> {
    if (!existsSync(this.filePath)) {
      return {};
    }
    try {
      return JSON.parse(readFileSync(this.filePath, "utf8")) as Record<string, string>;
    } catch {
      throw new CredentialError(
        CredentialErrorReason.CORRUPT_ENTRY,
        "Credential store file is corrupt"
      );
    }
  }

  private persist(content: Record<string, string>): void {
    mkdirSync(dirname(this.filePath), { recursive: true });
    writeFileSync(this.filePath, JSON.stringify(content, null, 2), "utf8");
  }
}

class DpapiLikeProtector implements SecretProtector {
  protect(plaintext: string): string {
    const nonce = randomUUID();
    const digest = createHash("sha256").update(`${nonce}:${plaintext}`, "utf8").digest("base64");
    return `${nonce}:${digest}:${Buffer.from(plaintext, "utf8").toString("base64")}`;
  }

  unprotect(ciphertext: string): string {
    const parts = ciphertext.split(":");
    if (parts.length !== 3) {
      throw new CredentialError(CredentialErrorReason.CORRUPT_ENTRY, "Corrupt credential blob");
    }
    try {
      return Buffer.from(parts[2], "base64").toString("utf8");
    } catch {
      throw new CredentialError(CredentialErrorReason.CORRUPT_ENTRY, "Corrupt credential blob");
    }
  }
}

export class WindowsCredentialProvider implements CredentialProvider {
  private readonly store: SecretStore;
  private readonly protector: SecretProtector;

  constructor(options?: { store?: SecretStore; protector?: SecretProtector; filePath?: string }) {
    this.store =
      options?.store ??
      new JsonFileStore(options?.filePath ?? `${process.cwd()}\\pc-agent-plugin\\.secrets\\credentials.json`);
    this.protector = options?.protector ?? new DpapiLikeProtector();
  }

  async readSecret(secretRef: string): Promise<string> {
    this.validateSecretRef(secretRef);
    const encrypted = this.store.read(secretRef);
    if (encrypted == null) {
      throw new CredentialError(CredentialErrorReason.NOT_FOUND, "Secret ref not found");
    }
    return this.protector.unprotect(encrypted);
  }

  async upsertSecret(secretRef: string, secret: string): Promise<void> {
    this.validateSecretRef(secretRef);
    if (!secret || !secret.trim()) {
      throw new CredentialError(CredentialErrorReason.INVALID_INPUT, "Secret must not be blank");
    }
    const encrypted = this.protector.protect(secret);
    this.store.write(secretRef, encrypted);
  }

  async deleteSecret(secretRef: string): Promise<void> {
    this.validateSecretRef(secretRef);
    this.store.delete(secretRef);
  }

  toString(): string {
    return `WindowsCredentialProvider{entries=${this.store.keys().length}}`;
  }

  private validateSecretRef(secretRef: string): void {
    if (!secretRef || !secretRef.trim()) {
      throw new CredentialError(
        CredentialErrorReason.INVALID_INPUT,
        "Secret reference must not be blank"
      );
    }
  }
}

export const __testing = {
  JsonFileStore,
  DpapiLikeProtector
};
