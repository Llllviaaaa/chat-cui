export type CliCredentialState = "ACTIVE" | "DISABLED" | "ROTATING";

export interface CliAuthConfig {
  tenantId: string;
  clientId: string;
  ak: string;
  secretRef: string;
  state: CliCredentialState;
  warning: string;
}

export class CliAuthConfigError extends Error {}

export class CliAuthConfigLoader {
  load(rawConfig: Record<string, string | undefined>): CliAuthConfig {
    const tenantId = this.require(rawConfig, "tenant_id");
    const clientId = this.require(rawConfig, "client_id");
    const ak = this.require(rawConfig, "ak");
    const secretRef = this.require(rawConfig, "secret_ref");
    const state = this.parseState(rawConfig.state);

    if (state === "DISABLED") {
      throw new CliAuthConfigError("Credential state DISABLED blocks startup");
    }

    return {
      tenantId,
      clientId,
      ak,
      secretRef,
      state,
      warning:
        state === "ROTATING"
          ? "Credential is rotating. Continue with warning in phase 01.1."
          : ""
    };
  }

  fromEnvironment(env: NodeJS.ProcessEnv = process.env): CliAuthConfig {
    return this.load({
      tenant_id: env.CHATCUI_TENANT_ID,
      client_id: env.CHATCUI_CLIENT_ID,
      ak: env.CHATCUI_AK,
      secret_ref: env.CHATCUI_SECRET_REF,
      state: env.CHATCUI_CREDENTIAL_STATE
    });
  }

  sanitizeForLog(config: Record<string, string | undefined>): Record<string, string> {
    const safe: Record<string, string> = {};
    for (const [key, value] of Object.entries(config)) {
      const normalized = key.toLowerCase();
      if (
        normalized.includes("secret") ||
        normalized.includes("signature") ||
        normalized === "sk"
      ) {
        safe[key] = "***REDACTED***";
      } else {
        safe[key] = value ?? "";
      }
    }
    return safe;
  }

  private require(raw: Record<string, string | undefined>, key: string): string {
    const value = raw[key];
    if (!value || !value.trim()) {
      throw new CliAuthConfigError(`Missing required field: ${key}`);
    }
    return value.trim();
  }

  private parseState(rawState: string | undefined): CliCredentialState {
    if (!rawState || !rawState.trim()) {
      return "ACTIVE";
    }
    const normalized = rawState.trim().toUpperCase();
    if (normalized === "ACTIVE" || normalized === "DISABLED" || normalized === "ROTATING") {
      return normalized;
    }
    throw new CliAuthConfigError(`Unsupported credential state: ${rawState}`);
  }
}
