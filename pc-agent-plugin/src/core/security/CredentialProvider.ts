import { CredentialError } from "./CredentialErrors";

export interface CredentialProvider {
  readSecret(secretRef: string): Promise<string>;
  upsertSecret(secretRef: string, secret: string): Promise<void>;
  deleteSecret(secretRef: string): Promise<void>;
}

export interface CredentialRecord {
  secretRef: string;
  encryptedSecret: string;
}

export type CredentialFailure = CredentialError;
