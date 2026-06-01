<template>
  <div class="forgot-password-page">
    <div class="forgot-password-container">
      <div class="forgot-password-left">
        <div class="logo-area">
          <div class="logo-icon">
            <el-icon :size="28"><Lock /></el-icon>
          </div>
          <div class="logo-text">Spring Boot Auth</div>
          <div class="logo-sub">找回密码</div>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="0"
          size="large"
          @keyup.enter="handleReset"
        >
          <el-form-item prop="email">
            <el-input
              v-model="form.email"
              placeholder="请输入注册邮箱地址"
              :prefix-icon="Message"
              @input="onEmailInput"
            />
          </el-form-item>

          <el-form-item prop="code">
            <div class="code-row">
              <el-input
                v-model="form.code"
                placeholder="请输入邮箱验证码"
                :prefix-icon="Key"
                class="code-input"
              />
              <el-button
                class="code-btn"
                :disabled="!emailValid || countdown > 0"
                @click="handleSendCode"
                :loading="sending"
              >
                {{ countdown > 0 ? countdown + 's 后重发' : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入新密码（至少6位）"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button
              class="btn-reset"
              :loading="loading"
              @click="handleReset"
            >
              {{ loading ? '重置中...' : '重 置 密 码' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="links">
          <span>想起密码？</span>
          <router-link to="/login" class="link-item">立即登录</router-link>
        </div>
      </div>

      <div class="forgot-password-right">
        <div class="permission-title">
          <el-icon :size="18"><Promotion /></el-icon>
          <span>安全找回密码流程：</span>
        </div>

        <ul class="feature-list">
          <li class="feature-item">
            <div class="feature-icon"><el-icon><Message /></el-icon></div>
            <div class="feature-text">
              <strong>邮箱验证码验证</strong>
              <p>6位数字验证码发送至注册邮箱，5分钟有效，60秒防刷</p>
            </div>
          </li>
          <li class="feature-item">
            <div class="feature-icon"><el-icon><Lock /></el-icon></div>
            <div class="feature-text">
              <strong>BCrypt 安全加密</strong>
              <p>新密码采用 BCrypt 算法加密存储，自适应哈希 + 随机盐</p>
            </div>
          </li>
          <li class="feature-item">
            <div class="feature-icon"><el-icon><Warning /></el-icon></div>
            <div class="feature-text">
              <strong>全局设备下线</strong>
              <p>密码重置后自动踢掉所有已登录设备，强制重新验证身份</p>
            </div>
          </li>
        </ul>

        <div class="tips-card">
          <el-alert title="温馨提示" type="warning" :closable="false" show-icon>
            <template #default>
              <p>重置密码后，所有设备上的登录状态将失效，需使用新密码重新登录。</p>
            </template>
          </el-alert>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Promotion, Message, Key, Lock, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { sendResetCode, resetPassword } from '@/api/user'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({
  email: '',
  code: '',
  password: '',
  confirmPassword: '',
})

// 邮箱格式校验
const emailReg = /^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[a-zA-Z]{2,}$/
const emailValid = computed(() => emailReg.test(form.email))

const onEmailInput = () => { /* computed 自动响应 */ }

const validateEmail = (rule, value, callback) => {
  if (!emailReg.test(value)) {
    callback(new Error('请输入正确的邮箱格式'))
  } else {
    callback()
  }
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  email: [
    { required: true, message: '请输入注册邮箱地址', trigger: 'blur' },
    { validator: validateEmail, trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入邮箱验证码', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

// 发送密码重置验证码
const handleSendCode = async () => {
  if (!emailValid.value) {
    ElMessage.warning('请先输入正确的邮箱地址')
    return
  }
  sending.value = true
  try {
    await sendResetCode(form.email)
    ElMessage.success('验证码已发送至邮箱，请查收')
    countdown.value = 60
    countdownTimer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(countdownTimer)
    }, 1000)
  } catch { /* 错误由拦截器统一提示 */ }
  finally { sending.value = false }
}

// 重置密码
const handleReset = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await resetPassword(form.email, form.password, form.code)
    ElMessage.success('密码重置成功，即将跳转登录页')
    clearInterval(countdownTimer)
    setTimeout(() => router.push('/login'), 800)
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  clearInterval(countdownTimer)
})
</script>

<style scoped>
* { margin: 0; padding: 0; box-sizing: border-box; }

.forgot-password-page {
  background-color: #f5f5f5;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.forgot-password-container {
  display: flex;
  width: 960px;
  min-height: 600px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.forgot-password-left {
  width: 50%;
  padding: 36px 48px;
}

.forgot-password-left :deep(.el-form-item) {
  margin-bottom: 20px;
}

.logo-area { text-align: center; margin-bottom: 22px; }
.logo-icon { display: inline-flex; align-items: center; justify-content: center; width: 50px; height: 50px; background-color: #31c27c; border-radius: 50%; color: #fff; margin-bottom: 8px; }
.logo-text { font-size: 22px; font-weight: bold; color: #333; }
.logo-sub { font-size: 13px; color: #999; margin-top: 4px; }

.code-row { display: flex; gap: 10px; }
.code-input { flex: 1; }
.code-btn { width: 130px; height: 42px; background-color: #fff; border: 1px solid #31c27c; color: #31c27c; font-size: 13px; border-radius: 4px; white-space: nowrap; flex-shrink: 0; }
.code-btn:hover:not(:disabled) { background-color: #e8f8ef; }
.code-btn:disabled { color: #bbb; border-color: #e5e5e5; cursor: not-allowed; }

.btn-reset { width: 100%; height: 42px; background-color: #31c27c; border-color: #31c27c; color: #fff; font-size: 16px; border-radius: 4px; }
.btn-reset:hover, .btn-reset:focus { background-color: #2aae6a; border-color: #2aae6a; }

.links { text-align: center; font-size: 13px; color: #666; }
.link-item { color: #31c27c; text-decoration: none; font-weight: 500; }
.link-item:hover { text-decoration: underline; }

.forgot-password-right { width: 50%; background-color: #f9fbf9; padding: 36px 48px; border-left: 1px solid #e8e8e8; display: flex; flex-direction: column; }
.permission-title { font-size: 16px; color: #333; margin-bottom: 24px; display: flex; align-items: center; gap: 8px; font-weight: 500; }
.feature-list { list-style: none; flex: 1; }
.feature-item { display: flex; gap: 12px; margin-bottom: 20px; padding-bottom: 16px; border-bottom: 1px solid #f0f0f0; }
.feature-item:last-child { border-bottom: none; }
.feature-icon { width: 36px; height: 36px; border-radius: 50%; background-color: #e8f8ef; color: #31c27c; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.feature-text strong { display: block; font-size: 14px; color: #333; margin-bottom: 4px; }
.feature-text p { font-size: 12px; color: #999; line-height: 1.5; margin: 0; }
.tips-card { margin-top: auto; }

@media (max-width: 768px) {
  .forgot-password-container { flex-direction: column; width: 92%; min-height: auto; }
  .forgot-password-left, .forgot-password-right { width: 100%; }
  .forgot-password-right { border-left: none; border-top: 1px solid #e8e8e8; }
  .forgot-password-left { padding: 28px 24px; }
  .forgot-password-right { padding: 28px 24px; }
  .code-btn { width: 110px; font-size: 12px; }
  .captcha-img { width: 100px; }
}
</style>
