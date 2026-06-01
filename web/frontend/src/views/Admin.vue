<template>
  <div class="admin-container">
    <el-container>
      <el-header class="admin-header">
        <span class="logo">管理后台</span>
        <div class="header-right">
          <el-button plain size="default" @click="$router.push('/home')">
            <el-icon><ArrowLeft /></el-icon>
            返回首页
          </el-button>
          <el-button type="danger" plain size="default" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-header>

      <el-main>
        <div class="content-card">
          <div class="card-title">
            <el-icon :size="18"><UserFilled /></el-icon>
            <span>用户列表</span>
          </div>

          <el-table
            :data="tableData"
            stripe
            border
            v-loading="loading"
            empty-text="暂无用户数据"
            style="width: 100%"
          >
            <el-table-column prop="id" label="ID" width="80" align="center" />
            <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
            <el-table-column prop="role" label="角色" width="120" align="center">
              <template #default="{ row }">
                <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" effect="light">
                  {{ row.role === 'admin' ? '管理员' : '普通用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nickname" label="昵称" width="160" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.nickname">{{ row.nickname }}</span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="头像" width="80" align="center">
              <template #default="{ row }">
                <el-avatar v-if="row.avatar" :src="row.avatar" :size="32" />
                <el-icon v-else :size="24" color="#ccc"><UserFilled /></el-icon>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap" v-if="total > 0">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :page-sizes="[5, 10, 20]"
              :total="total"
              layout="total, sizes, prev, pager, next"
              @size-change="onSizeChange"
              @current-change="onPageChange"
            />
          </div>
        </div>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, UserFilled } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { getAdminUsers } from '@/api/user'
import { logout } from '@/api/user'
import { removeToken } from '@/utils/token'

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    const res = await getAdminUsers(currentPage.value, pageSize.value)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch {
    // 错误由 Axios 拦截器统一提示
  } finally {
    loading.value = false
  }
}

const onPageChange = (page) => {
  currentPage.value = page
  loadUsers()
}

const onSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadUsers()
}

// 退出登录
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
      // 即使服务端登出失败，本地也清除
    }
    removeToken()
    router.push('/login')
  } catch {
    // 取消操作
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.admin-container {
  min-height: 100vh;
  background: #f0f2f5;
}
.admin-header {
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
  gap: 12px;
}
.content-card {
  max-width: 960px;
  margin: 24px auto;
  background: #fff;
  border-radius: 12px;
  padding: 28px 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}
.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
.text-muted {
  color: #c0c4cc;
}
</style>
