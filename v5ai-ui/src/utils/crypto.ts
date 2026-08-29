import * as CryptoJS from 'crypto-js'
import JSEncrypt from 'jsencrypt'

const rsaPublicKey = import.meta.env.VITE_APP_RSA_PUBLIC_KEY ?? ''
const rsaPrivateKey = import.meta.env.VITE_APP_RSA_PRIVATE_KEY ?? ''

/**
 * 生成随机 AES 密钥（16 位字符，对应后端 AES-128）。
 */
function randomAesKey(length = 16): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  const bytes = crypto.getRandomValues(new Uint8Array(length))
  return Array.from(bytes, (b) => chars[b % chars.length]).join('')
}

/**
 * AES 加密（ECB/Pkcs7，与后端 hutool AES/ECB/PKCS5Padding 兼容），输出 base64。
 */
function encryptAes(data: string, key: string): string {
  return CryptoJS.AES.encrypt(data, CryptoJS.enc.Utf8.parse(key), {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7
  }).toString()
}

/**
 * AES 解密（ECB/Pkcs7）。
 */
function decryptAes(data: string, key: string): string {
  return CryptoJS.AES.decrypt(data, CryptoJS.enc.Utf8.parse(key), {
    mode: CryptoJS.mode.ECB,
    padding: CryptoJS.pad.Pkcs7
  }).toString(CryptoJS.enc.Utf8)
}

/**
 * RSA 公钥加密（PKCS1 v1.5，与后端 hutool RSA/ECB/PKCS1Padding 兼容），输出 base64。
 */
function encryptByRsa(data: string, publicKey = rsaPublicKey): string {
  const encryptor = new JSEncrypt()
  encryptor.setPublicKey(publicKey)
  const result = encryptor.encrypt(data)
  if (!result) {
    throw new Error('RSA 加密失败，请检查公钥配置')
  }
  return result
}

/**
 * RSA 私钥解密（用于解密后端加密响应中的 AES 密钥）。
 */
function decryptByRsa(data: string, privateKey = rsaPrivateKey): string {
  const decryptor = new JSEncrypt()
  decryptor.setPrivateKey(privateKey)
  const result = decryptor.decrypt(data)
  return result === false ? '' : result
}

/**
 * 接口请求加密（与后端 @ApiEncrypt 请求解密协议对应）：
 * 随机 AES 密钥加密 JSON 请求体，AES 密钥经 base64 后用 RSA 公钥加密放入 encrypt-key 请求头。
 *
 * @param payload 明文请求体
 * @returns 加密后的请求体与 encrypt-key 请求头值
 */
export function encryptRequest(payload: unknown): { body: string; encryptKey: string } {
  const aesKey = randomAesKey(16)
  const body = encryptAes(JSON.stringify(payload), aesKey)
  const encryptKey = encryptByRsa(CryptoJS.enc.Base64.stringify(CryptoJS.enc.Utf8.parse(aesKey)))
  return { body, encryptKey }
}

/**
 * 接口响应解密（与后端 @ApiEncrypt(response=true) 响应加密协议对应）：
 * 先用 RSA 私钥解密响应头中的 AES 密钥，再 AES 解密响应体。
 */
export function decryptResponse(body: string, encryptKeyHeader: string): string {
  const aesKey = decryptByRsa(encryptKeyHeader)
  return decryptAes(body, aesKey)
}
