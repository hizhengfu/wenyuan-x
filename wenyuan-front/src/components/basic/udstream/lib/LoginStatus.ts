export enum LoginStatus {
  NoStatus, // Used temporarily at startup and after logout to set focus on the correct fields
  EnterCredentials,
  NoServerURL,
  Pending,
  ConnectionError,
  AuthError,
  TimeSync,
  SecurityError,
  NegotiationError,
  ProxyError,
  ProxyAuthRequired,
  ProxyAuthFailed,
  Timeout,
  SessionLimitExceeded,
  InvalidLicense,
  NotSupported,
  OtherError,

  ForgotPassword,
  ForgotPasswordPending,
  ForgotPasswordCheckEmail,
  ForgotPasswordTryPortal,

  Register,
  RegisterPending,
  RegisterCheckEmail,
  RegisterTryPortal,

  CheckingServerVersion,
  WaitingForBrowser,

  Count
}

export const LoginStatusKeys: string[] = [
  "loginMessageCredentials",
  "loginMessageCredentials",
  "loginEnterURL",
  "loginMessageChecking",
  "loginErrorConnection",
  "loginErrorAuth",
  "loginErrorTimeSync",
  "loginErrorSecurity",
  "loginErrorNegotiate",
  "loginErrorProxy",
  "loginErrorProxyAuthPending",
  "loginErrorProxyAuthFailed",
  "loginErrorTimeout",
  "loginErrorSessionLimitExceeded",
  "loginErrorInvalidLicense",
  "loginErrorNotSupported",
  "loginErrorOther",

  "loginForgot",
  "loginForgotPending",
  "loginForgotCheckEmail",
  "loginForgotTryPortal",

  "loginRegister",
  "loginRegisterPending",
  "loginRegisterCheckEmail",
  "loginRegisterTryPortal",

  "loginCheckingServerVersion",
  "loginWaitingUserBrowser",
];

if (LoginStatus.Count !== LoginStatusKeys.length) {
  throw new Error("LoginStatus and LoginStatusKeys are not the same length!");
}
