// API 配置中心 —— 所有后端接口地址统一从此导入，禁止硬编码
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
