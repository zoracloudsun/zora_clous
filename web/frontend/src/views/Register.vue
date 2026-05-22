<template>
  <div class="register-page">
    <div class="register-container">
      <!-- 左侧：注册表单区 -->
      <div class="register-left">
        <!-- Logo 区域 -->
        <div class="logo-area">
          <div class="logo-icon">
            <el-icon :size="28"><UserFilled /></el-icon>
          </div>
          <div class="logo-text">Spring Boot Auth</div>
          <div class="logo-sub">创建新账号</div>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="0"
          size="large"
          @keyup.enter="handleRegister"
        >
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名（3-50个字符）"
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码（至少6位）"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="请再次输入密码"
              :prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button
              class="btn-register"
              :loading="loading"
              @click="handleRegister"
            >
              {{ loading ? '注册中...' : '注 册' }}
            </el-button>
          </el-form-item>
        </el-form>

        <div class="links">
          <span>已有账号？</span>
          <router-link to="/login" class="link-item">立即登录</router-link>
        </div>

        <!-- 微信注册入口（预留） -->
        <div class="other-register">
          <div class="divider">
            <span class="divider-text">其他注册方式</span>
          </div>
          <div class="social-icons">
            <div class="social-icon wechat" title="微信注册">
              <span class="icon-placeholder">微</span>
            </div>
            <div class="social-icon qq" title="QQ注册">
              <span class="icon-placeholder">Q</span>
            </div>
          </div>
          <p class="social-hint">微信/QQ 快捷注册功能开发中</p>
        </div>
      </div>

      <!-- 右侧：注册权益信息 -->
      <div class="register-right">
        <div class="permission-title">
          <el-icon :size="18"><Promotion /></el-icon>
          <span>注册即享以下权益：</span>
        </div>

        <ul class="feature-list">
          <li class="feature-item">
            <div class="feature-icon">
              <el-icon><Lock /></el-icon>
            </div>
            <div class="feature-text">
              <strong>安全加密存储</strong>
              <p>密码采用 BCrypt 加密算法，每次加密结果不同，抗彩虹表攻击</p>
            </div>
          </li>
          <li class="feature-item">
            <div class="feature-icon">
              <el-icon><Refresh /></el-icon>
            </div>
            <div class="feature-text">
              <strong>无感 Token 刷新</strong>
              <p>accessToken 30分钟 + refreshToken 7天，自动刷新免频繁登录</p>
            </div>
          </li>
          <li class="feature-item">
            <div class="feature-icon">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="feature-text">
              <strong>单设备登录保护</strong>
              <p>同一账号只允许一个设备在线，新登录自动踢掉旧设备</p>
            </div>
          </li>
          <li class="feature-item">
            <div class="feature-icon">
              <el-icon><Checked /></el-icon>
            </div>
            <div class="feature-text">
              <strong>微信扫码登录</strong>
              <p>后续将支持微信 OAuth 2.0 扫码快捷登录，敬请期待</p>
            </div>
          </li>
        </ul>

        <div class="agreement">
          注册即同意
          <a href="#" @click.prevent="showAgreement('service')">《服务协议》</a>和
          <a href="#" @click.prevent="showAgreement('privacy')">《隐私保护指引》</a>
        </div>

        <div class="tips-card">
          <el-alert
            title="密码建议"
            type="success"
            :closable="false"
            show-icon
          >
            <template #default>
              <p>建议使用大小写字母 + 数字 + 特殊符号的组合，长度不少于 8 位</p>
            </template>
          </el-alert>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, UserFilled, Promotion, Refresh, Monitor, Checked } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { register } from '@/api/user'

const router = useRouter()
const formRef = ref(null)
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度在3到50个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

const handleRegister = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await register(form.username, form.password)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } finally {
    loading.value = false
  }
}

const showAgreement = (type) => {
  ElMessage.info(`${type === 'service' ? '服务协议' : '隐私保护指引'}详情页预留`)
}
</script>

<style scoped>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.register-page {
  background-color: #f5f5f5;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.register-container {
  display: flex;
  width: 900px;
  min-height: 600px;
  background-color: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 20px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

/* ======== 左侧注册区域 ======== */
.register-left {
  width: 50%;
  padding: 40px;
}

.logo-area {
  text-align: center;
  margin-bottom: 24px;
}

.logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  background-color: #31c27c;
  border-radius: 50%;
  color: #fff;
  margin-bottom: 8px;
}

.logo-text {
  font-size: 22px;
  font-weight: bold;
  color: #333;
}

.logo-sub {
  font-size: 13px;
  color: #999;
  margin-top: 4px;
}

/* 注册按钮 */
.btn-register {
  width: 100%;
  height: 42px;
  background-color: #31c27c;
  border-color: #31c27c;
  color: #fff;
  font-size: 16px;
  border-radius: 4px;
}

.btn-register:hover,
.btn-register:focus {
  background-color: #2aae6a;
  border-color: #2aae6a;
}

/* 链接 */
.links {
  text-align: center;
  font-size: 13px;
  color: #666;
  margin-bottom: 20px;
}

.link-item {
  color: #31c27c;
  text-decoration: none;
  font-weight: 500;
}

.link-item:hover {
  text-decoration: underline;
}

/* 其他注册方式 */
.other-register {
  margin-top: 4px;
}

.divider {
  display: flex;
  align-items: center;
  margin-bottom: 14px;
}

.divider::before,
.divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background-color: #e5e5e5;
}

.divider-text {
  padding: 0 16px;
  font-size: 12px;
  color: #999;
}

.social-icons {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 10px;
}

.social-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
}

.social-icon:hover {
  transform: scale(1.1);
}

.social-icon.wechat {
  background-color: #07c160;
  color: #fff;
}

.social-icon.qq {
  background-color: #12b7f5;
  color: #fff;
}

.icon-placeholder {
  font-size: 18px;
  font-weight: bold;
}

.social-hint {
  text-align: center;
  font-size: 11px;
  color: #bbb;
}

/* ======== 右侧权益区域 ======== */
.register-right {
  width: 50%;
  background-color: #f9fbf9;
  padding: 40px;
  border-left: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
}

.permission-title {
  font-size: 16px;
  color: #333;
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.feature-list {
  list-style: none;
  flex: 1;
}

.feature-item {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.feature-item:last-child {
  border-bottom: none;
}

.feature-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #e8f8ef;
  color: #31c27c;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-text strong {
  display: block;
  font-size: 14px;
  color: #333;
  margin-bottom: 4px;
}

.feature-text p {
  font-size: 12px;
  color: #999;
  line-height: 1.5;
  margin: 0;
}

.agreement {
  font-size: 12px;
  color: #999;
  line-height: 1.6;
  margin-bottom: 16px;
}

.agreement a {
  color: #31c27c;
  text-decoration: none;
}

.agreement a:hover {
  text-decoration: underline;
}

.tips-card {
  margin-top: auto;
}

/* ======== 响应式 ======== */
@media (max-width: 768px) {
  .register-container {
    flex-direction: column;
    width: 92%;
    min-height: auto;
  }

  .register-left,
  .register-right {
    width: 100%;
  }

  .register-right {
    border-left: none;
    border-top: 1px solid #e8e8e8;
  }

  .register-left {
    padding: 28px 24px;
  }

  .register-right {
    padding: 24px;
  }
}
</style>
