import { describe, expect, it } from "vitest";
import {
  CredentialError,
  CredentialErrorReason
} from "../../../src/core/security/CredentialErrors";
import { WindowsCredentialProvider } from "../../../src/core/security/WindowsCredentialProvider";

class MemoryStore {
  private readonly data = new Map<string, string>();

  read(secretRef: string): string | null {
    return this.data.get(secretRef) ?? null;
  }

  write(secretRef: string, encryptedSecret: string): void {
    this.data.set(secretRef, encryptedSecret);
  }

  delete(secretRef: string): void {
    this.data.delete(secretRef);
  }

  keys(): string[] {
    return [...this.data.keys()];
  }
}

class PrefixProtector {
  protect(plaintext: string): string {
    return `enc:${plaintext}`;
  }

  unprotect(ciphertext: string): string {
    if (!ciphertext.startsWith("enc:")) {
      throw new CredentialError(CredentialErrorReason.CORRUPT_ENTRY, "Corrupt secret");
    }
    return ciphertext.slice(4);
  }
}

describe("WindowsCredentialProvider", () => {
  it("reads and writes secrets with typed behavior", async () => {
    const provider = new WindowsCredentialProvider({
      store: new MemoryStore(),
      protector: new PrefixProtector()
    });

    await provider.upsertSecret("wincred://a", "secret-v1");
    await expect(provider.readSecret("wincred://a")).resolves.toBe("secret-v1");
  });

  it("returns NOT_FOUND for missing ref", async () => {
    const provider = new WindowsCredentialProvider({
      store: new MemoryStore(),
      protector: new PrefixProtector()
    });

    await expect(provider.readSecret("missing")).rejects.toMatchObject({
      reason: CredentialErrorReason.NOT_FOUND
    });
  });

  it("does not leak secret in toString output", async () => {
    const provider = new WindowsCredentialProvider({
      store: new MemoryStore(),
      protector: new PrefixProtector()
    });

    await provider.upsertSecret("wincred://b", "super-sensitive");
    expect(provider.toString()).toContain("entries=1");
    expect(provider.toString()).not.toContain("super-sensitive");
  });

  it("throws INVALID_INPUT for blank values", async () => {
    const provider = new WindowsCredentialProvider({
      store: new MemoryStore(),
      protector: new PrefixProtector()
    });

    await expect(provider.upsertSecret("", "x")).rejects.toMatchObject({
      reason: CredentialErrorReason.INVALID_INPUT
    });
    await expect(provider.upsertSecret("ref", " ")).rejects.toMatchObject({
      reason: CredentialErrorReason.INVALID_INPUT
    });
  });
});
