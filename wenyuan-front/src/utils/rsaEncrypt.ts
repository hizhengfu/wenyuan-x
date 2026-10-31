import JSEncrypt from 'jsencrypt'

const publicKey: string = 'MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDB5XNv/DH/3XDUbc+OJ3AJgLfJf8rm5kvsOvT+Ph0CAiGVIGjTEaccrh0QQ78wOeLRvciQhJ+8af5uvepSC7ossGfGaKfTDrt1yPPA8TuGGCEEXwdAycX1ViTB4fHXiyrlDbowhTZwT1GNU/YpoxLk9xUdNjvNz0X/IaWRd91UzQIDAQAB'

/**
 *  RSA加密
 **/
export function rsaEncrypt(txt: string) {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  return encryptor.encrypt(txt)
}

// /**
//  *  RSA验证签名
//  **/
// export function verify(txt: string, signature: string) {
//   const encryptor = new JSEncrypt()
//   encryptor.setPublicKey(publicKey)
//   return encryptor.verify(txt, signature, CryptoJS.SHA256)
// }
//
//
// /**
//  *  RSA解密
//  **/
// export function rsaDecrypt(txt: string, privateKey: string) {
//   const encryptor = new JSEncrypt()
//   encryptor.setPrivateKey(privateKey)
//   return encryptor.decrypt(txt)
// }
