<template>
  <div class="page-wrap">
    <div class="x-card" style="max-width: 860px; margin: 0 auto">
      <h2 class="page-title">🚩 发起结伴行程</h2>
      <el-alert
        type="warning"
        :closable="false"
        class="mb-16"
        title="结伴安全须知"
        description="仅面向三校已完成学籍认证的同学；请如实填写行程信息，出发前设置家长/辅导员紧急联系人（家校知情），建议全员购买旅游意外险；野路/高风险行程请充分评估，切勿冒险。"
        show-icon
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="行程标题" prop="title">
          <el-input v-model="form.title" placeholder="如：周末张家界三日两夜结伴（长沙出发）" maxlength="40" show-word-limit />
        </el-form-item>

        <div class="flex gap-12" style="flex-wrap: wrap">
          <el-form-item label="目的地" prop="destination" style="flex: 1; min-width: 220px">
            <el-input v-model="form.destination" placeholder="如：张家界国家森林公园" />
          </el-form-item>
          <el-form-item label="出发时间" prop="departAt" style="flex: 1; min-width: 220px">
            <el-date-picker
              v-model="form.departAt"
              type="datetime"
              placeholder="选择出发时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
        </div>

        <div class="flex gap-12" style="flex-wrap: wrap">
          <el-form-item label="人数上限" prop="maxMembers" style="flex: 1; min-width: 200px">
            <el-input-number v-model="form.maxMembers" :min="2" :max="20" />
          </el-form-item>
          <el-form-item label="人均预算" prop="budget" style="flex: 1; min-width: 200px">
            <el-input-number v-model="form.budget" :min="0" :step="100" :precision="0" />
            <span class="sub-text" style="margin-left: 8px">元/人</span>
          </el-form-item>
        </div>

        <el-form-item label="出行方式" prop="transport">
          <el-radio-group v-model="form.transport">
            <el-radio-button label="TRAIN">高铁/火车</el-radio-button>
            <el-radio-button label="BUS">长途大巴</el-radio-button>
            <el-radio-button label="CAR">自驾/租车</el-radio-button>
            <el-radio-button label="CARPOOL">拼车/顺风车</el-radio-button>
            <el-radio-button label="BIKE">骑行/徒步</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="集合地点" prop="meetPoint">
          <el-input v-model="form.meetPoint" placeholder="如：长沙南站东广场进站口08:00集合" />
        </el-form-item>

        <!-- 结伴偏好：发布组队时设定，作为偏好匹配与推荐的依据 -->
        <el-divider content-position="left">结伴偏好（用于匹配队友）</el-divider>

        <el-form-item label="游玩节奏" prop="paceType">
          <el-radio-group v-model="form.paceType">
            <el-radio v-for="p in PACE_OPTIONS" :key="p.value" :label="p.value">
              {{ p.label }}
              <span class="sub-text">（{{ p.desc }}）</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="预期天数" prop="prefDays">
          <el-input-number v-model="form.prefDays" :min="1" :max="15" />
          <span class="sub-text" style="margin-left: 8px">天</span>
        </el-form-item>

        <el-form-item label="兴趣标签">
          <el-checkbox-group v-model="tagList">
            <el-checkbox v-for="t in TRIP_INTEREST_TAGS" :key="t" :label="t">{{ t }}</el-checkbox>
          </el-checkbox-group>
          <div class="sub-text">勾选本队的主打玩法，报名时系统会据此计算申请人与队伍的匹配度</div>
        </el-form-item>

        <el-form-item label="开放范围" prop="schoolScope">
          <el-radio-group v-model="form.schoolScope">
            <el-radio label="ALL">三校跨校（中南/湖南师大/湖大同学均可报名）</el-radio>
            <el-radio label="SAME">仅本校（只收同校同学）</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="风险等级" prop="riskLevel">
          <el-radio-group v-model="form.riskLevel">
            <el-radio label="LOW">低风险（成熟景区/Citywalk）</el-radio>
            <el-radio label="MEDIUM">中风险（近郊徒步/露营）</el-radio>
            <el-radio label="HIGH">高风险（野路/秘境/溯溪）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert
          v-if="form.riskLevel === 'HIGH'"
          type="error"
          :closable="false"
          class="mb-12"
          title="高风险行程提示"
          description="高风险行程须全员确认装备与经验匹配，强烈建议购买保险、向辅导员/家长完成行程报备，并在行程中开启实时位置共享；天气不佳或身体不适时立即终止。"
          show-icon
        />
        <el-alert
          v-else-if="form.riskLevel === 'MEDIUM'"
          type="warning"
          :closable="false"
          class="mb-12"
          title="中风险行程请结伴而行、准备基础户外装备，保持通讯畅通。"
          show-icon
        />

        <el-form-item label="行程说明" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            placeholder="行程安排、报名要求、装备清单、费用 AA 方式、注意事项等"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>

        <el-form-item>
          <el-button @click="$router.back()">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">发布行程</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { tripApi } from '@/api'
import { PACE_OPTIONS, TRIP_INTEREST_TAGS } from '@/utils/format'

const router = useRouter()
const formRef = ref()
const saving = ref(false)
const tagList = ref([])

const form = ref({
  title: '',
  destination: '',
  departAt: '',
  maxMembers: 6,
  budget: 500,
  transport: 'TRAIN',
  meetPoint: '',
  riskLevel: 'LOW',
  description: '',
  // 结伴偏好
  paceType: 'BALANCED',
  prefDays: 2,
  schoolScope: 'ALL'
})

const rules = {
  title: [{ required: true, message: '请填写行程标题', trigger: 'blur' }],
  destination: [{ required: true, message: '请填写目的地', trigger: 'blur' }],
  departAt: [{ required: true, message: '请选择出发时间', trigger: 'change' }],
  maxMembers: [{ required: true, message: '设置人数上限', trigger: 'change' }],
  budget: [{ required: true, message: '填写人均预算', trigger: 'change' }],
  transport: [{ required: true, message: '选择出行方式', trigger: 'change' }],
  meetPoint: [{ required: true, message: '填写集合地点', trigger: 'blur' }],
  riskLevel: [{ required: true, message: '选择风险等级', trigger: 'change' }],
  description: [{ required: true, message: '填写行程说明', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  saving.value = true
  try {
    const data = await tripApi.create({
      ...form.value,
      interestTags: tagList.value.join(',')
    })
    ElMessage.success('行程发布成功，等待同学报名吧！')
    router.replace(data?.id ? `/trips/${data.id}` : '/trips')
  } finally {
    saving.value = false
  }
}
</script>
