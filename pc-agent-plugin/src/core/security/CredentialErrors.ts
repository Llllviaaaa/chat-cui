export enum CredentialErrorReason {
  NOT_FOUND = "NOT_FOUND",
  ACCESS_DENIED = "ACCESS_DENIED",
  CORRUPT_ENTRY = "CORRUPT_ENTRY",
  INVALID_INPUT = "INVALID_INPUT"
}

export class CredentialError extends Error {
  readonly reason: CredentialErrorReason;

  constructor(reason: CredentialErrorReason, message: string) {
    super(message);
    this.reason = reason;
  }

  safeMessage(): string {
    switch (this.reason) {
      case CredentialErrorReason.NOT_FOUND:
        return "Secret reference not found";
      case CredentialErrorReason.ACCESS_DENIED:
        return "Credential store access denied";
      case CredentialErrorReason.CORRUPT_ENTRY:
        return "Credential entry is corrupt";
      case CredentialErrorReason.INVALID_INPUT:
        return "Credential input is invalid";
      default:
        return "Credential operation failed";
    }
  }
}
