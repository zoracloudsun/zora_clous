<template>
  <div class="home-container">
    <el-container>
      <el-header class="home-header">
        <span class="logo">Spring Boot Auth System</span>
        <div class="header-right">
          <span class="welcome">欢迎回来</span>
          <el-button
            type="info"
            plain
            size="default"
            @click="$router.push('/search')"
          >
            全文搜索
          </el-button>
          <el-button
            type="warning"
            plain
            size="default"
            @click="$router.push('/dashboard')"
          >
            数据仪表盘
          </el-button>
          <el-button
            type="success"
            plain
            size="default"
            @click="$router.push('/chat')"
          >
            AI 对话
          </el-button>
          <el-button
            v-if="role === 'admin'"
            type="primary"
            plain
            size="default"
            @click="$router.push('/admin')"
          >
            管理后台
          </el-button>
          <el-button type="danger" plain size="default" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-header>

      <el-main>
        <div class="content-card">
          <el-result
            icon="success"
            title="鉴权通过"
            sub-title="你已成功通过 JWT Token 鉴权，可以正常访问受保护资源"
          >
            <template #extra>
              <el-descriptions :column="1" border>
                <el-descriptions-item label="接口状态">
                  <el-tag type="success">GET /user/info 200 OK</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="认证方式">JWT 双 Token + Redis</el-descriptions-item>
                <el-descriptions-item label="Token 刷新">
                  <el-tag type="success">accessToken 30min + refreshToken 7天</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="单设备登录">
                  <el-tag type="warning">已启用</el-tag>
                </el-descriptions-item>
              </el-descriptions>
            </template>
          </el-result>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { logout } from '@/api/user'
import { removeToken, getRole } from '@/utils/token'

const router = useRouter()
const role = ref(getRole())

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    try {
      await logout()
    } catch {
      // 即使服务端登出失败，本地也应清除 Token
    }
    removeToken()
    router.push('/login')
  } catch {
    // 取消操作
  }
}
</script>

<style scoped>
.home-container {
  min-height: 100vh;
  background: #f0f2f5;
}
.home-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  padding: 0 32px;
}
.logo {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.welcome {
  color: #606266;
}
.content-card {
  max-width: 720px;
  margin: 60px auto;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}
</style>
