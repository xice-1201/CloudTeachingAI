/**
 * 表单验证工具函数
 */

/**
 * 验证邮箱
 */
export function isEmail(email: string): boolean {
  const reg = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
  return reg.test(email)
}

/**
 * 验证手机号（中国大陆）
 */
export function isPhone(phone: string): boolean {
  const reg = /^1[3-9]\d{9}$/
  return reg.test(phone)
}

/**
 * 验证密码强度（至少8位，包含大小写字母和数字）
 */
export function isStrongPassword(password: string): boolean {
  const reg = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*?&]{8,}$/
  return reg.test(password)
}

/**
 * 验证 URL
 */
export function isURL(url: string): boolean {
  try {
    new URL(url)
    return true
  } catch {
    return false
  }
}

/**
 * Element Plus 表单验证规则生成器
 */
export const rules = {
  required: (message = '此项为必填项') => ({
    required: true,
    message,
    trigger: 'blur',
  }),

  email: (message = '请输入有效的邮箱地址') => ({
    validator: (_rule: any, value: string, callback: any) => {
      if (!value) {
        callback()
      } else if (!isEmail(value)) {
        callback(new Error(message))
      } else {
        callback()
      }
    },
    trigger: 'blur',
  }),

  phone: (message = '请输入有效的手机号') => ({
    validator: (_rule: any, value: string, callback: any) => {
      if (!value) {
        callback()
      } else if (!isPhone(value)) {
        callback(new Error(message))
      } else {
        callback()
      }
    },
    trigger: 'blur',
  }),

  password: (message = '密码至少8位，包含大小写字母和数字') => ({
    validator: (_rule: any, value: string, callback: any) => {
      if (!value) {
        callback()
      } else if (!isStrongPassword(value)) {
        callback(new Error(message))
      } else {
        callback()
      }
    },
    trigger: 'blur',
  }),

  minLength: (min: number, message?: string) => ({
    min,
    message: message || `长度不能少于${min}个字符`,
    trigger: 'blur',
  }),

  maxLength: (max: number, message?: string) => ({
    max,
    message: message || `长度不能超过${max}个字符`,
    trigger: 'blur',
  }),

  range: (min: number, max: number, message?: string) => ({
    min,
    max,
    message: message || `长度必须在${min}-${max}个字符之间`,
    trigger: 'blur',
  }),
}
