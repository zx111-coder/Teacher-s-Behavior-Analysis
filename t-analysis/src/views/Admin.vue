<template>
    <div class="admin-container">
      <el-row :gutter="20">
        <el-col :span="24">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>用户管理</span>
                <el-button type="primary" size="small" @click="showAddDialog">添加用户</el-button>
              </div>
            </template>
            <!-- 用户列表表格 -->
            <el-table :data="users" border style="width: 100%">
              <el-table-column prop="username" label="用户名"></el-table-column>
              <el-table-column prop="role" label="角色">
                <!-- 角色标签化展示 -->
                <template #default="scope">
                  <el-tag :type="scope.row.role === 'admin' ? 'danger' : 'primary'">
                    {{ scope.row.role }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="创建时间">
                <template #default="scope">
                  {{ formatDate(scope.row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180">
                <!-- 编辑/删除按钮 -->
                <template #default="scope">
                  <el-button size="small" @click="handleEdit(scope.row)">编辑</el-button>
                  <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
      <!-- 新增/编辑弹窗 -->
      <el-dialog v-model="dialogVisible" :title="dialogTitle" width="30%">
        <!-- formRef 是用于获取 Element Plus 中 el - form 组件实例的引用 -->
        <el-form :model="form" :rules="rules" ref="formRef">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username"></el-input>
          </el-form-item>
          <el-form-item label="密码" prop="password" >
            <el-input v-model="form.password" :type="passwordVisible ? 'text' : 'password'">
              <template #append><!--#append在输入框的右侧插入内容-->
                <el-button @click="passwordVisible = !passwordVisible" type="text">
                  {{ passwordVisible ? '隐藏' : '显示' }}
                </el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="角色" prop="role">
            <el-select v-model="form.role" placeholder="请选择角色">
              <el-option label="管理员" value="admin"></el-option>
              <el-option label="普通用户" value="user"></el-option>
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer> <!--#footer在弹窗底部插入内容-->
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </template>
      </el-dialog>
    </div>
  </template>
  
  <script>
  import { ref, onMounted } from 'vue'
  import { ElMessage, ElMessageBox } from 'element-plus'
  import axios from 'axios'
  import { useUserStore } from '@/store/user.js'
  export default {
    name: 'Admin',
    setup() {
    const users = ref([]) // 最终的用户列表
    const passwordVisible = ref(false) // 密码输入框的显示/隐藏状态
    const userStore = useUserStore()  // Pinia Store（用户状态）
    const loading = ref(false) // 加载状态
    const dialogVisible = ref(false)// 弹窗显隐
    const dialogTitle = ref('')// 弹窗标题
    const isAdd = ref(false) // 区分新增/编辑(前端展示界面)
    const formRef = ref(null)// 表单引用（用于校验）
    const form = ref({ // 表单数据
      id: null,
      username: '',
      password: '',
      role: 'user',
      createdAt:""
    })


    // 格式化日期
    const formatDate = (dateString) => {
      if (!dateString) return "无日期";
      try {
        const date = new Date(dateString);
        if (isNaN(date.getTime())) return dateString; // 如果解析失败，返回原始字符串
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        const hour = String(date.getHours()).padStart(2, "0");
        const minute = String(date.getMinutes()).padStart(2, "0");
        
        return `${year}-${month}-${day} ${hour}:${minute}`;
      } catch (e) {
        console.error("日期格式化失败:", e);
        return dateString; // 异常时返回原始字符串
      }
    };
  // 表单校验规则
    const rules = {
      username: [
        { required: true, message: '请输入用户名', trigger: 'blur' }
      ],
      password: [
        { required: true, message: '请输入密码', trigger: 'blur' }
        //{ min: 6, message: '密码长度不能少于6位', trigger: 'blur' }//password长度限制
      ],
      role: [
        { required: true, message: '请选择角色', trigger: 'change' }
      ]
    }

    // 获取用户列表
    const fetchUsers = async () => {
      loading.value = true
      try {
        // 这里假设后端需要当前用户的用户名作为参数
        const currentUser = userStore.username;
        console.log('当前用户:', currentUser)
        const response = await axios.get('/api/admin', {
          params: {
            username: currentUser
          }
        })
        console.log('后端用户列表的响应信息:', response)
        users.value = response.data
      } catch (error) {
        ElMessage.error('获取用户列表失败')
        console.error('Error fetching users:', error)
      } finally {
        loading.value = false
      }
    }

    onMounted(() => {
      fetchUsers()
    })

    // 显示添加用户对话框
    const showAddDialog = () => {
      dialogTitle.value = '添加用户'
      isAdd.value = true
      form.value = {
        id: null,
        username: '',
        password: '',
        role: 'user'
      }
      dialogVisible.value = true
    }
    // 处理编辑用户
    const handleEdit = (user) => {
      dialogTitle.value = '编辑用户'
      isAdd.value = false // 设置为编辑状态
      //将待编辑用户的信息赋值给表单，用于弹窗回显
      form.value = {
        id: user.id,
        username: user.username,
        password: user.password,
        role: user.role,
        createdAt: user.createdAt 
      }
      passwordVisible.value = false; // 默认隐藏
      //打开编辑弹窗，让用户进行修改操作
      dialogVisible.value = true
    }
    
    // 删除用户的处理
    const handleDelete = async (user) => {
      try {
          await ElMessageBox.confirm(`确定删除用户 ${user.username} 吗?`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
         // 调用删除接口
          await axios.post('/api/change', 
            user,//json格式
            { params: { actionType: '1' } 
          })
          .then(res => {
            ElMessage.success('删除成功')
            console.log('删除的响应信息:', res);
          fetchUsers() // 刷新列表
        })
      } catch (error) {
        if (error !== 'cancel') {
          ElMessage.error('删除失败')
          console.error('删除失败的错误信息', error)
        }
      }
    }
    // 提交表单
    const submitForm = async () => {
      try {
        const valid = await formRef.value.validate()//确保数据符合校验规则
        if (!valid) return
        if (isAdd.value) {
          // 调用添加用户接口
          await axios.post('/api/register', {
            username: form.value.username,
            password: form.value.password,
            role: form.value.role
          },{
          headers: {
            'Content-Type': 'application/json'
          }
        })
        .then(res => {
          console.log('添加成功的响应:', res);
          ElMessage.success('添加成功')
          // 更新本地用户数据
          users.value.push({
            id: res.data.data.id, // 假设后端返回新用户的ID
            username: form.value.username,
            role: form.value.role,
            createdAt: new Date().toISOString() // 使用当前时间作为创建时间
          })
        })
          
        } else {
          // 调用编辑用户接口
          const userData = {
            createdAt: new Date().toISOString(), // 修改创建时间
            id: form.value.id,
            username: form.value.username,
            role: form.value.role,
            password: form.value.password // 确保密码也被更新
          }
          console.log('编辑用户数据:', userData)
          const res=await axios.post('/api/change', userData, // 完整的Users对象
          { 
            params: { actionType: '2' } 
          })
          console.log('编辑的响应:', res);
          if(res.data.code === 0) {
            ElMessage.error('编辑失败')
          } else {
            ElMessage.success('编辑成功')
          }
        }
        dialogVisible.value = false
        fetchUsers() // 刷新列表
      } catch (error) {
        if (error.response) {
          // 服务器响应了但状态码不在2xx范围
          ElMessage.error(`操作失败: ${error.response.data.message || '服务器错误'}`)
        } else {
          ElMessage.error('操作失败')
        }
      }
    }

    return {
      users,
      loading,
      dialogVisible,
      dialogTitle,
      isAdd,
      form,
      rules,
      formRef,
      showAddDialog,
      handleEdit,
      handleDelete,
      submitForm,
      formatDate,
      passwordVisible
    }
  }
}
</script>

  
  <style scoped>
  .admin-container {
    padding: 20px;
  }
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  </style>