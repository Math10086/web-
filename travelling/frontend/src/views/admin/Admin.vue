<template>
  <div class="page-wrap">
    <div class="flex-between mb-16">
      <h2 class="page-title" style="margin: 0">⚙️ 学旅圈管理后台</h2>
      <span class="sub-text">
        后台运营与治理 · 内容 / 广告 / 资质审核 · 举报监管 · 信誉投诉复核 · 用户治理 · 交易结算 · 数据可视化（结伴出行、地图路线等 C 端前台功能不对管理员开放）
      </span>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ① 待审攻略 -->
      <el-tab-pane label="内容审核" name="posts">
        <div class="x-card">
          <div class="card-title">待审核攻略 / 帖子（{{ pendingPosts.length }}）</div>
          <el-table :data="pendingPosts" v-loading="loading.posts" size="small" empty-text="审核队列已清空">
            <el-table-column label="类型" width="80">
              <template #default="{ row }">
                <el-tag size="small">{{ POST_TYPE_MAP[row.type] || row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="标题" prop="title" min-width="180" show-overflow-tooltip />
            <el-table-column label="作者" width="120">
              <template #default="{ row }">{{ row.author?.nickname || row.authorName }}</template>
            </el-table-column>
            <el-table-column label="城市" prop="city" width="90" />
            <el-table-column label="提交时间" width="150">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="230">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="previewPost(row)">预览</el-button>
                <el-button link type="success" size="small" @click="auditPost(row, true)">通过</el-button>
                <el-button link type="danger" size="small" @click="rejectPost(row)">驳回</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ② 举报处理 -->
      <el-tab-pane label="举报处理" name="reports">
        <div class="x-card">
          <div class="card-title">用户举报队列</div>
          <el-table :data="reports" v-loading="loading.reports" size="small" empty-text="暂无举报">
            <el-table-column label="类型" prop="targetType" width="90" />
            <el-table-column label="对象 ID" prop="targetId" width="90" />
            <el-table-column label="原因" prop="reason" width="120" />
            <el-table-column label="证据" prop="evidence" min-width="200" show-overflow-tooltip />
            <el-table-column label="举报人" width="110">
              <template #default="{ row }">{{ row.reporterName || row.userId || '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'HANDLED' ? 'success' : 'warning'">
                  {{ row.status === 'HANDLED' || row.handled ? '已处理' : '待处理' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="处理" width="220">
              <template #default="{ row }">
                <el-input
                  v-model="handleMap[row.id]"
                  size="small"
                  placeholder="处理结果说明"
                  style="width: 130px"
                />
                <el-button
                  type="primary"
                  link
                  size="small"
                  :disabled="row.status === 'HANDLED' || row.handled"
                  @click="handleReport(row)"
                >
                  提交处理
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ③ 信誉投诉处理：结伴后的投诉 → 申诉 → 终审，与内容举报分开处理 -->
      <el-tab-pane label="信誉投诉" name="complaints" lazy>
        <div v-loading="complaintLoading" class="section-stack">
          <div class="overview-grid">
            <div
              v-for="(item, i) in complaintKpiCards"
              :key="item.key"
              class="overview-card"
              :style="{ background: overviewColors[i % overviewColors.length] }"
            >
              <div class="ov-num">{{ item.value }}</div>
              <div class="ov-label">{{ item.label }}</div>
            </div>
          </div>

          <div class="x-card">
            <div class="card-title">
              结伴信誉投诉工作台
              <span class="sub-text" style="margin-left: auto">
                核实 → 扣信誉分 · 申诉 → 终审回滚 · 处理结果实时通知投诉双方
              </span>
            </div>

            <div class="flex gap-8 mb-12" style="flex-wrap: wrap">
              <el-select
                v-model="complaintQuery.status"
                placeholder="全部状态"
                clearable
                style="width: 150px"
                @change="loadComplaints(1)"
              >
                <el-option
                  v-for="(label, key) in COMPLAINT_STATUS_MAP"
                  :key="key"
                  :label="label"
                  :value="key"
                />
              </el-select>
              <el-select
                v-model="complaintQuery.type"
                placeholder="全部类型"
                clearable
                style="width: 150px"
                @change="loadComplaints(1)"
              >
                <el-option
                  v-for="(label, key) in COMPLAINT_TYPE_MAP"
                  :key="key"
                  :label="label"
                  :value="key"
                />
              </el-select>
              <el-input
                v-model="complaintQuery.keyword"
                placeholder="行程标题 / 投诉人 / 被投诉人"
                style="width: 260px"
                :prefix-icon="Search"
                clearable
                @keyup.enter="loadComplaints(1)"
                @clear="loadComplaints(1)"
              />
              <el-button type="primary" @click="loadComplaints(1)">查询</el-button>
              <el-button @click="resetComplaintQuery">重置</el-button>
              <el-button link type="primary" @click="loadComplaints(complaintQuery.page)">
                刷新
              </el-button>
            </div>

            <el-table :data="complaints" size="small" empty-text="没有符合条件的投诉记录">
              <el-table-column label="ID" prop="id" width="60" />
              <el-table-column label="关联行程" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">
                  <span>{{ row.tripTitle || '（行程已删除）' }}</span>
                  <span class="sub-text"> #{{ row.tripId }}</span>
                </template>
              </el-table-column>
              <el-table-column label="投诉类型" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="COMPLAINT_TYPE_TAG[row.type] || 'info'">
                    {{ row.typeName || COMPLAINT_TYPE_MAP[row.type] || row.type }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="投诉人 → 被投诉人" min-width="190">
                <template #default="{ row }">
                  <span>{{ row.reporterName || `#${row.reporterId}` }}</span>
                  <span class="sub-text"> → </span>
                  <span style="font-weight: 500">{{ row.targetName || `#${row.targetId}` }}</span>
                  <el-tag
                    v-if="row.targetComplaintCount > 1"
                    size="small"
                    type="warning"
                    effect="plain"
                    style="margin-left: 6px"
                  >
                    累计被投诉{{ row.targetComplaintCount }}次
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="被投诉人信誉" width="120">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain" :type="creditTagType(row.targetCreditScore)">
                    {{ row.targetCreditScore ?? '—' }}分
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="已生效扣分" width="100">
                <template #default="{ row }">
                  <span :style="{ color: (row.creditDelta || 0) < 0 ? '#f5222d' : '#8c8c8c' }">
                    {{ row.creditDelta ? (row.creditDelta > 0 ? `+${row.creditDelta}` : row.creditDelta) : '0' }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="COMPLAINT_STATUS_TAG[row.status] || 'info'">
                    {{ complaintStatusText(row) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="提交时间" width="150">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openComplaint(row)">
                    {{ canHandleComplaint(row) ? '处理' : '详情' }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="flex-between mt-12">
              <span class="sub-text">共{{ complaintTotal }}条</span>
              <el-pagination
                v-model:current-page="complaintQuery.page"
                v-model:page-size="complaintQuery.size"
                :total="complaintTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next"
                @current-change="loadComplaints"
                @size-change="loadComplaints(1)"
              />
            </div>
          </div>

          <div class="x-card">
            <div class="card-title">投诉类型分布（全量）</div>
            <div ref="complaintPieEl" class="chart-box" style="height: 280px"></div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ④ 广告审核 -->
      <el-tab-pane label="广告审核" name="ads">
        <div class="x-card">
          <div class="card-title">
            广告投放审核
            <el-button type="primary" size="small" style="margin-left: auto" @click="openAdCreate">
              新增广告
            </el-button>
          </div>
          <el-table :data="ads" v-loading="loading.ads" size="small" empty-text="暂无广告">
            <el-table-column label="素材" width="120">
              <template #default="{ row }">
                <el-image :src="row.image" style="width: 90px; height: 50px; border-radius: 6px" fit="cover" />
              </template>
            </el-table-column>
            <el-table-column label="标识" width="70">
              <template #default>
                <el-tag type="warning" size="small">广告</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="标题" prop="title" min-width="140" show-overflow-tooltip />
            <el-table-column label="广告位" prop="slot" width="90" />
            <el-table-column label="广告主" prop="advertiser" width="120" />
            <el-table-column label="定向城市" prop="targetCity" width="90" />
            <el-table-column label="投放周期" min-width="200">
              <template #default="{ row }">
                {{ formatDate(row.startAt) }} ~ {{ formatDate(row.endAt) }}
              </template>
            </el-table-column>
            <el-table-column label="价格" width="90">
              <template #default="{ row }">¥{{ row.price }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="auditTagType(row.status)">
                  {{ AUDIT_STATUS_MAP[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <template v-if="row.status === 'PENDING' || !row.status">
                  <el-button link type="success" size="small" @click="auditAd(row, true)">通过</el-button>
                  <el-button link type="danger" size="small" @click="auditAd(row, false)">驳回</el-button>
                </template>
                <span v-else class="sub-text">已处理</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ⑤ 商家 / 导游审核 -->
      <el-tab-pane label="资质审核" name="audit">
        <div class="section-stack">
          <div class="x-card">
            <div class="card-title">商家入驻审核</div>
            <el-table :data="merchants" v-loading="loading.audit" size="small" empty-text="暂无商家申请">
              <el-table-column label="店铺" prop="shopName" min-width="140" />
              <el-table-column label="资质" prop="qualification" min-width="200" show-overflow-tooltip />
              <el-table-column label="介绍" prop="description" min-width="160" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="auditTagType(row.status)">
                    {{ AUDIT_STATUS_MAP[row.status] || '待审核' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button
                    v-if="row.status !== 'APPROVED' && row.status !== 'REJECTED'"
                    link type="success" size="small" @click="auditMerchant(row, true)"
                  >通过</el-button>
                  <el-button
                    v-if="row.status !== 'APPROVED' && row.status !== 'REJECTED'"
                    link type="danger" size="small" @click="auditMerchant(row, false)"
                  >驳回</el-button>
                  <span v-else class="sub-text">已处理</span>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="x-card">
            <div class="card-title">导游资质审核</div>
            <el-table :data="guides" size="small" empty-text="暂无导游申请">
              <el-table-column label="申请人" prop="nickname" width="120" />
              <el-table-column label="擅长主题" prop="themes" min-width="160" show-overflow-tooltip />
              <el-table-column label="介绍" prop="intro" min-width="220" show-overflow-tooltip />
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="auditTagType(row.status)">
                    {{ AUDIT_STATUS_MAP[row.status] || '待审核' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button
                    v-if="row.status !== 'APPROVED' && row.status !== 'REJECTED'"
                    link type="success" size="small" @click="auditGuide(row, true)"
                  >通过</el-button>
                  <el-button
                    v-if="row.status !== 'APPROVED' && row.status !== 'REJECTED'"
                    link type="danger" size="small" @click="auditGuide(row, false)"
                  >驳回</el-button>
                  <span v-else class="sub-text">已处理</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <!-- ⑥ 用户治理 -->
      <el-tab-pane label="用户治理" name="users">
        <div class="x-card">
          <div class="flex gap-8 mb-12">
            <el-input
              v-model="userKeyword"
              placeholder="按用户名 / 昵称搜索"
              style="width: 260px"
              :prefix-icon="Search"
              @keyup.enter="searchUsers"
            />
            <el-button type="primary" @click="searchUsers">搜索</el-button>
          </div>
          <el-table :data="users" v-loading="loading.users" size="small" empty-text="输入关键字搜索用户">
            <el-table-column label="ID" prop="id" width="70" />
            <el-table-column label="头像" width="70">
              <template #default="{ row }">
                <el-avatar :size="34" :src="row.avatar">{{ row.nickname?.charAt(0) }}</el-avatar>
              </template>
            </el-table-column>
            <el-table-column label="账号" prop="username" width="120" />
            <el-table-column label="昵称" prop="nickname" width="120" />
            <el-table-column label="学校" width="130">
              <template #default="{ row }">{{ SCHOOL_MAP[row.school] || row.school || '—' }}</template>
            </el-table-column>
            <el-table-column label="信誉分" width="150">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.creditScore"
                  :min="0"
                  :max="150"
                  size="small"
                  controls-position="right"
                  style="width: 110px"
                />
              </template>
            </el-table-column>
            <el-table-column label="角色" width="130">
              <template #default="{ row }">
                <el-select v-model="row.role" size="small">
                  <el-option label="普通用户" value="USER" />
                  <el-option label="攻略博主" value="BLOGGER" />
                  <el-option label="商家" value="MERCHANT" />
                  <el-option label="导游" value="GUIDE" />
                  <el-option label="管理员" value="ADMIN" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="封禁" width="90">
              <template #default="{ row }">
                <el-switch v-model="row.banned" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="saveUser(row)">保存</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ⑦ 交易结算（管理员专属：商城佣金 + 向导分成 + 结算发放记录） -->
      <el-tab-pane label="交易结算" name="settlement" lazy>
        <div v-loading="settleLoading" class="section-stack">
          <div class="overview-grid">
            <div
              v-for="(v, k) in settleKpis"
              :key="k"
              class="overview-card"
              :style="{ background: overviewColors[Object.keys(settleKpis).indexOf(k) % overviewColors.length] }"
            >
              <div class="ov-num">{{ v }}</div>
              <div class="ov-label">{{ settleKpiLabel(k) }}</div>
            </div>
          </div>

          <div class="x-card">
            <div class="card-title">
              🏪 商城待结算（平台佣金{{ ((settlement.shopCommissionRate || 0.05) * 100).toFixed(0) }}%）
              <span class="sub-text" style="margin-left: auto">仅统计用户已确认收货的订单</span>
            </div>
            <el-table :data="settlement.shopRows || []" size="small" empty-text="暂无待结算的商城订单">
              <el-table-column label="店铺" prop="targetName" min-width="150" show-overflow-tooltip />
              <el-table-column label="订单数" prop="orderCount" width="80" />
              <el-table-column label="成交额" width="110">
                <template #default="{ row }">¥{{ row.grossAmount }}</template>
              </el-table-column>
              <el-table-column label="平台佣金" width="110">
                <template #default="{ row }">¥{{ row.commissionAmount }}</template>
              </el-table-column>
              <el-table-column label="应付商家" width="120">
                <template #default="{ row }">
                  <span style="color: #f5222d; font-weight: 700">¥{{ row.payableAmount }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="doPayout(row)">
                    结算打款
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="x-card">
            <div class="card-title">
              🧭 导游待结算（按订单分成比例）
              <span class="sub-text" style="margin-left: auto">仅统计游客已完成的向导订单</span>
            </div>
            <el-table :data="settlement.guideRows || []" size="small" empty-text="暂无待结算的向导订单">
              <el-table-column label="导游" prop="targetName" min-width="130" show-overflow-tooltip />
              <el-table-column label="订单数" prop="orderCount" width="80" />
              <el-table-column label="成交额" width="110">
                <template #default="{ row }">¥{{ row.grossAmount }}</template>
              </el-table-column>
              <el-table-column label="平台抽成" width="110">
                <template #default="{ row }">
                  ¥{{ row.commissionAmount }}
                  <span class="sub-text">（{{ ((row.commissionRate || 0) * 100).toFixed(0) }}%）</span>
                </template>
              </el-table-column>
              <el-table-column label="应付导游" width="120">
                <template #default="{ row }">
                  <span style="color: #f5222d; font-weight: 700">¥{{ row.payableAmount }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="doPayout(row)">
                    结算打款
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="x-card">
            <div class="card-title">🧾 结算发放记录</div>
            <el-table :data="settlement.records || []" size="small" empty-text="还没有结算发放记录">
              <el-table-column label="时间" width="160">
                <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column label="对象" min-width="160">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.type === 'SHOP' ? 'primary' : 'success'" effect="plain">
                    {{ row.type === 'SHOP' ? '商城' : '导游' }}
                  </el-tag>
                  {{ row.targetName }}
                </template>
              </el-table-column>
              <el-table-column label="订单数" prop="orderCount" width="80" />
              <el-table-column label="成交额" width="110">
                <template #default="{ row }">¥{{ row.grossAmount }}</template>
              </el-table-column>
              <el-table-column label="佣金/抽成" width="110">
                <template #default="{ row }">¥{{ row.commissionAmount }}</template>
              </el-table-column>
              <el-table-column label="已发放" width="110">
                <template #default="{ row }">¥{{ row.payableAmount }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" type="success">已发放</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <!-- ⑧ 数据看板（管理员专属：顶部导航已不暴露，只在管理后台内查看） -->
      <el-tab-pane label="数据看板" name="stats" lazy>
        <div class="section-stack">
          <div class="overview-grid">
            <div
              v-for="(v, k) in overview"
              :key="k"
              class="overview-card"
              :style="{ background: overviewColors[Object.keys(overview).indexOf(k) % overviewColors.length] }"
            >
              <div class="ov-num">{{ v }}</div>
              <div class="ov-label">{{ overviewLabel(k) }}</div>
            </div>
          </div>

          <div class="x-card">
            <div class="card-title">热门景点分布</div>
            <div class="two-chart">
              <div ref="pieEl" class="chart-box"></div>
              <div ref="barEl" class="chart-box"></div>
            </div>
          </div>

          <div class="x-card">
            <div class="card-title">圈层活跃度</div>
            <div ref="circleEl" class="chart-box"></div>
          </div>

          <div class="x-card">
            <div class="card-title">社区热词云</div>
            <SimpleWordCloud :words="wordCloudData" style="height: 360px" />
          </div>

          <!-- 平台行为数据看板：内嵌完整看板组件，与 /stats 共用一套图表实现 -->
          <div class="flex-between" style="margin-top: 4px">
            <h3 style="margin: 0">📈 平台行为数据看板</h3>
            <span class="sub-text">城市活跃 · 路线模式 · 景点到访 · 内容构成 · 路线城市 · 发帖趋势</span>
          </div>
          <StatsBoard embedded />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 帖子预览 -->
    <el-dialog v-model="previewVisible" title="帖子预览（审核）" width="640px">
      <h2 v-if="previewRow">{{ previewRow.title }}</h2>
      <div class="sub-text" v-if="previewRow">
        {{ previewRow.author?.nickname }} · {{ previewRow.city }} ·
        {{ formatTime(previewRow.createdAt) }}
      </div>
      <el-image
        v-for="(img, i) in previewImages"
        :key="i"
        :src="img"
        style="width: 100%; border-radius: 8px; margin-top: 10px"
      />
      <div class="detail-content mt-12" v-if="previewRow">{{ previewRow.content }}</div>
    </el-dialog>

    <!-- 驳回原因 -->
    <el-dialog v-model="rejectVisible" title="驳回原因" width="440px">
      <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请填写驳回理由，将反馈给作者" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 信誉投诉处理（核实 / 驳回 / 维持 / 推翻） -->
    <el-dialog v-model="complaintVisible" title="信誉投诉处理" width="720px" top="6vh">
      <div v-if="complaintRow" class="section-stack">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="投诉编号">{{ complaintRow.id }}</el-descriptions-item>
          <el-descriptions-item label="关联行程">
            {{ complaintRow.tripTitle || `行程 #${complaintRow.tripId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="投诉人">
            {{ complaintRow.reporterName || `#${complaintRow.reporterId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="被投诉人">
            {{ complaintRow.targetName || `#${complaintRow.targetId}` }}
            <span class="sub-text">（当前{{ complaintRow.targetCreditScore ?? '—' }}分）</span>
          </el-descriptions-item>
          <el-descriptions-item label="投诉类型">
            {{ complaintRow.typeName || COMPLAINT_TYPE_MAP[complaintRow.type] || complaintRow.type }}
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatTime(complaintRow.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="被投诉人历史">
            共{{ complaintRow.targetComplaintCount || 0 }}次，
            核实成立{{ complaintRow.targetUpheldCount || 0 }}次
          </el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag size="small" :type="COMPLAINT_STATUS_TAG[complaintRow.status] || 'info'">
              {{ complaintStatusText(complaintRow) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="投诉说明" :span="2">
            {{ complaintRow.description || '（未填写）' }}
          </el-descriptions-item>
          <el-descriptions-item label="证据材料" :span="2">
            {{ complaintRow.evidence || '（未提供）' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="complaintRow.appealReason" label="申诉理由" :span="2">
            <div>{{ complaintRow.appealReason }}</div>
            <div class="sub-text">申诉时间：{{ formatTime(complaintRow.appealAt) }}</div>
          </el-descriptions-item>
          <el-descriptions-item v-if="complaintRow.result" label="历史处理" :span="2">
            {{ complaintRow.result }}
            <span class="sub-text">
              （{{ complaintRow.handledByName || '管理员' }} · {{ formatTime(complaintRow.handledAt) }}）
            </span>
          </el-descriptions-item>
          <el-descriptions-item v-if="complaintRow.status === 'FINAL'" label="终审结论" :span="2">
            <el-tag
              size="small"
              :type="complaintRow.finalAction === 'OVERTURNED' ? 'success' : 'danger'"
            >
              {{ complaintRow.finalAction === 'OVERTURNED' ? '申诉成立，信誉分已回滚' : '维持原判' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <template v-if="canHandleComplaint(complaintRow)">
          <el-divider content-position="left">处理决定</el-divider>
          <el-radio-group v-model="handleForm.action">
            <el-radio
              v-for="opt in handleOptions"
              :key="opt.value"
              :value="opt.value"
              border
              style="margin-bottom: 8px"
            >
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
          <div class="sub-text" style="margin: 2px 0 10px">
            {{ currentHandleHint }}
          </div>
          <el-input
            v-model="handleForm.result"
            type="textarea"
            :rows="3"
            :placeholder="handlePlaceholder"
          />
        </template>
        <el-alert
          v-else
          type="info"
          :closable="false"
          title="该投诉已终结（终审完成或判定不属实），无需再处理"
        />
      </div>
      <template #footer>
        <el-button @click="complaintVisible = false">关闭</el-button>
        <el-button
          v-if="complaintRow && canHandleComplaint(complaintRow)"
          type="primary"
          :loading="complaintSubmitting"
          @click="submitHandle"
        >
          提交处理
        </el-button>
      </template>
    </el-dialog>

    <!-- 新增广告 -->
    <el-dialog v-model="adCreateVisible" title="新增广告投放" width="540px">
      <el-form :model="adForm" label-width="92px">
        <el-form-item label="广告位">
          <el-select v-model="adForm.slot" style="width: 100%">
            <el-option label="开屏 BANNER" value="BANNER" />
            <el-option label="信息流 FEED" value="FEED" />
            <el-option label="侧边栏 SIDEBAR" value="SIDEBAR" />
            <el-option label="详情页 DETAIL" value="DETAIL" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="adForm.title" />
        </el-form-item>
        <el-form-item label="图片 URL">
          <el-input v-model="adForm.image" />
        </el-form-item>
        <el-form-item label="跳转链接">
          <el-input v-model="adForm.link" />
        </el-form-item>
        <el-form-item label="广告主">
          <el-input v-model="adForm.advertiser" />
        </el-form-item>
        <el-form-item label="定向城市">
          <el-input v-model="adForm.targetCity" />
        </el-form-item>
        <div class="flex gap-8">
          <el-form-item label="开始时间" style="flex: 1">
            <el-date-picker v-model="adForm.startAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" style="flex: 1">
            <el-date-picker v-model="adForm.endAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="价格">
          <el-input-number v-model="adForm.price" :min="0" :step="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adCreateVisible = false">取消</el-button>
        <el-button type="primary" @click="createAd">保存（待审核）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import SimpleWordCloud from '@/components/SimpleWordCloud.vue'
import StatsBoard from '@/views/stats/StatsBoard.vue'
import {
  adApi,
  creditApi,
  guideApi,
  merchantApi,
  postApi,
  reportApi,
  settlementApi,
  statsApi,
  userApi
} from '@/api'
import {
  AUDIT_STATUS_MAP,
  COMPLAINT_STATUS_MAP,
  COMPLAINT_STATUS_TAG,
  COMPLAINT_TYPE_MAP,
  COMPLAINT_TYPE_PENALTY,
  COMPLAINT_TYPE_TAG,
  creditTagType,
  formatDate,
  formatTime,
  POST_TYPE_MAP,
  SCHOOL_MAP,
  splitImages
} from '@/utils/format'

const activeTab = ref('posts')
const loading = reactive({
  posts: false,
  reports: false,
  ads: false,
  audit: false,
  users: false
})

const pendingPosts = ref([])
const reports = ref([])
const ads = ref([])
const merchants = ref([])
const guides = ref([])
const users = ref([])
const userKeyword = ref('')
const handleMap = reactive({})

const previewVisible = ref(false)
const previewRow = ref(null)
const rejectVisible = ref(false)
const rejectReason = ref('')
let rejectTarget = null

const adCreateVisible = ref(false)
const adForm = ref({})

function auditTagType(s) {
  if (s === 'APPROVED') return 'success'
  if (s === 'REJECTED') return 'danger'
  return 'warning'
}

/* ---------- 帖子审核 ---------- */
async function loadPosts() {
  loading.posts = true
  try {
    const data = await postApi.pending().catch(() => [])
    pendingPosts.value = data?.list || data || []
  } finally {
    loading.posts = false
  }
}

const previewImages = ref([])
function previewPost(row) {
  previewRow.value = row
  previewImages.value = splitImages(row.images)
  previewVisible.value = true
}

async function auditPost(row, approve) {
  await postApi.audit(row.id, {
    approve,
    reason: approve ? '审核通过' : '内容不符合社区规范'
  })
  ElMessage.success(approve ? '已通过' : '已驳回')
  loadPosts()
}

function rejectPost(row) {
  rejectTarget = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  await postApi.audit(rejectTarget.id, { approve: false, reason: rejectReason.value })
  ElMessage.success('已驳回')
  rejectVisible.value = false
  loadPosts()
}

/* ---------- 举报 ---------- */
async function loadReports() {
  loading.reports = true
  try {
    const data = await reportApi.list().catch(() => [])
    reports.value = data?.list || data || []
  } finally {
    loading.reports = false
  }
}

async function handleReport(row) {
  const result = handleMap[row.id]
  if (!result?.trim()) {
    ElMessage.warning('请填写处理结果')
    return
  }
  await reportApi.handle(row.id, result.trim())
  ElMessage.success('处理结果已提交')
  loadReports()
}

/* ---------- 信誉投诉处理 ---------- */
const complaintLoading = ref(false)
const complaintSubmitting = ref(false)
const complaints = ref([])
const complaintTotal = ref(0)
const complaintKpis = ref({})
const complaintVisible = ref(false)
const complaintRow = ref(null)
const complaintQuery = reactive({ status: '', type: '', keyword: '', page: 1, size: 10 })
const handleForm = reactive({ action: '', result: '' })
let complaintInited = false

/** 只有「待处理」「申诉中」两种状态需要管理员动作，其余仅可查看 */
function canHandleComplaint(row) {
  return !!row && (row.status === 'PENDING' || row.status === 'APPEALED')
}

function complaintStatusText(row) {
  if (row.status === 'FINAL') {
    return row.finalAction === 'OVERTURNED' ? '终审·申诉成立' : '终审·维持原判'
  }
  return COMPLAINT_STATUS_MAP[row.status] || row.status
}

const complaintKpiCards = computed(() => {
  const k = complaintKpis.value || {}
  return [
    { key: 'pending', label: '待处理投诉', value: k.pending ?? 0 },
    { key: 'appealed', label: '申诉待复核', value: k.appealed ?? 0 },
    { key: 'resolved', label: '已核实扣分', value: k.resolved ?? 0 },
    { key: 'rejected', label: '判定不属实', value: k.rejected ?? 0 },
    { key: 'final', label: '已终审', value: k.final ?? 0 },
    { key: 'penalizedPoints', label: '累计扣减信誉分', value: k.penalizedPoints ?? 0 },
    { key: 'overturnRate', label: '终审推翻率（%）', value: k.overturnRate ?? 0 }
  ]
})

const handleOptions = computed(() => {
  const s = complaintRow.value?.status
  if (s === 'PENDING') {
    return [
      { value: 'RESOLVED', label: '核实属实（扣被投诉人信誉分）' },
      { value: 'REJECTED', label: '判定不属实（不扣分）' }
    ]
  }
  if (s === 'APPEALED') {
    return [
      { value: 'UPHELD', label: '维持原判（申诉不成立）' },
      { value: 'OVERTURNED', label: '申诉成立（回滚已扣信誉分）' }
    ]
  }
  return []
})

const currentHandleHint = computed(() => {
  const row = complaintRow.value
  if (!row) return ''
  const penalty = COMPLAINT_TYPE_PENALTY[row.type] ?? 10
  const action = handleForm.action
  if (row.status === 'PENDING') {
    if (action === 'RESOLVED') {
      return `核实属实：将从「${row.targetName || '被投诉人'}」当前${row.targetCreditScore ?? '—'} 分中扣减${penalty} 分（下限0分），并写入信誉变动日志。`
    }
    if (action === 'REJECTED') {
      return '判定不属实：不产生任何信誉分变动，本次投诉终结。'
    }
    return '请选择处理决定。'
  }
  if (action === 'OVERTURNED') {
    const back = Math.abs(row.creditDelta || penalty)
    return `申诉成立：撤销原判，把已扣的${back} 分全额回滚给「${row.targetName || '被投诉人'}」。`
  }
  if (action === 'UPHELD') {
    return `维持原判：已扣的${Math.abs(row.creditDelta || penalty)} 分保留，申诉不成立，事件终结。`
  }
  return '申诉复核为终审，结论提交后不可再次变更，请谨慎判断。'
})

const handlePlaceholder = computed(() =>
  complaintRow.value?.status === 'PENDING'
    ? '处理结果说明（将展示给投诉双方并随通知推送，例如：已核对出行记录与群聊，确认被投诉人未到场）'
    : '终审意见（例如：被投诉人提供了当日医院就诊记录与定位截图，爽约事实不成立）'
)

async function loadComplaints(page) {
  if (typeof page === 'number') complaintQuery.page = page
  complaintLoading.value = true
  try {
    const data = await creditApi.adminComplaints({ ...complaintQuery })
    const rows = data?.list || []
    complaints.value = rows
    complaintTotal.value = data?.total ?? rows.length
    complaintKpis.value = data?.kpis || {}
    await nextTick()
    renderComplaintPie(data?.typeDistribution || [])
  } catch (e) {
    complaints.value = []
    complaintTotal.value = 0
  } finally {
    complaintLoading.value = false
  }
}

function resetComplaintQuery() {
  complaintQuery.status = ''
  complaintQuery.type = ''
  complaintQuery.keyword = ''
  loadComplaints(1)
}

function openComplaint(row) {
  complaintRow.value = { ...row }
  handleForm.action = row.status === 'APPEALED' ? 'UPHELD' : 'RESOLVED'
  handleForm.result = ''
  complaintVisible.value = true
}

async function submitHandle() {
  const row = complaintRow.value
  if (!row) return
  const opt = handleOptions.value.find((o) => o.value === handleForm.action)
  if (!opt) {
    ElMessage.warning('请选择处理决定')
    return
  }
  const finalRound = row.status === 'APPEALED'
  await ElMessageBox.confirm(
    `确认对投诉 #${row.id} 执行「${opt.label}」？` +
      (finalRound ? '终审结论提交后不可再次变更。' : '处理结果将实时通知投诉双方。'),
    '处理确认',
    { type: 'warning' }
  )
  complaintSubmitting.value = true
  try {
    await creditApi.handle(row.id, handleForm.action, handleForm.result?.trim() || opt.label)
    ElMessage.success('处理完成，已通知投诉双方')
    complaintVisible.value = false
    await loadComplaints(complaintQuery.page)
  } finally {
    complaintSubmitting.value = false
  }
}

const complaintPieEl = ref(null)
let complaintPie = null
function renderComplaintPie(dist) {
  if (!complaintPieEl.value) return
  if (!complaintPie) {
    complaintPie = makeChart(complaintPieEl.value)
  }
  const data = (dist || [])
    .filter((d) => (d.value || 0) > 0)
    .map((d) => ({ name: d.name || COMPLAINT_TYPE_MAP[d.type] || d.type, value: d.value }))
  complaintPie.setOption(
    {
      tooltip: { trigger: 'item', formatter: '{b}：{c} 件（{d}%）' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          type: 'pie',
          radius: ['42%', '66%'],
          center: ['50%', '45%'],
          avoidLabelOverlap: true,
          label: { formatter: '{b}\n{c} 件' },
          data: data.length ? data : [{ name: '暂无投诉记录', value: 1, itemStyle: { color: '#e8e8e8' } }]
        }
      ]
    },
    true
  )
  complaintPie.resize()
}

/* ---------- 广告 ---------- */
async function loadAds() {
  loading.ads = true
  try {
    const data = await adApi.admin().catch(() => [])
    ads.value = data?.list || data || []
  } finally {
    loading.ads = false
  }
}

async function auditAd(row, approve) {
  await ElMessageBox.confirm(
    approve ? `确认通过广告「${row.title}」？需确保素材合规并标注"广告"标识` : `确认驳回广告「${row.title}」？`,
    '广告审核',
    { type: approve ? 'success' : 'warning' }
  )
  await adApi.audit(row.id, approve)
  ElMessage.success(approve ? '广告已通过' : '广告已驳回')
  loadAds()
}

function openAdCreate() {
  adForm.value = {
    slot: 'BANNER',
    title: '',
    image: '',
    link: '',
    advertiser: '',
    targetCity: '',
    startAt: '',
    endAt: '',
    price: 100
  }
  adCreateVisible.value = true
}

async function createAd() {
  if (!adForm.value.title || !adForm.value.image) {
    ElMessage.warning('请填写标题与图片 URL')
    return
  }
  await adApi.create({ ...adForm.value })
  ElMessage.success('广告已创建，进入审核队列')
  adCreateVisible.value = false
  loadAds()
}

/* ---------- 商家/导游 ---------- */
async function loadAudit() {
  loading.audit = true
  try {
    const [m, g] = await Promise.allSettled([
      merchantApi.list('all'),
      guideApi.profiles('all')
    ])
    merchants.value = m.status === 'fulfilled' ? m.value?.list || m.value || [] : []
    const rawGuides = g.status === 'fulfilled' ? g.value?.list || g.value || [] : []
    // 兼容两种返回：扁平字段（nickname/themes/status）与嵌套 {profile,user}
    guides.value = rawGuides.map((row) => {
      const p = row?.profile || row || {}
      const u = row?.user || row || {}
      return {
        ...p,
        id: p.id,
        nickname: row?.nickname ?? u.nickname ?? p.nickname ?? '匿名用户',
        themes: p.themes,
        intro: p.intro,
        status: p.status
      }
    })
  } finally {
    loading.audit = false
  }
}

async function auditMerchant(row, approve) {
  await merchantApi.audit(row.id, approve)
  ElMessage.success(approve ? '商家已通过，可上架商品' : '已驳回')
  loadAudit()
}

async function auditGuide(row, approve) {
  await guideApi.auditProfile(row.id, approve)
  ElMessage.success(approve ? '导游已通过，可发布服务' : '已驳回')
  loadAudit()
}

/* ---------- 用户治理 ---------- */
async function searchUsers() {
  if (!userKeyword.value.trim()) {
    ElMessage.warning('请输入搜索关键字')
    return
  }
  loading.users = true
  try {
    const data = await userApi.search(userKeyword.value.trim()).catch(() => [])
    users.value = data?.list || data || []
  } finally {
    loading.users = false
  }
}

async function saveUser(row) {
  await ElMessageBox.confirm(
    `确认对用户「${row.nickname}」执行：${row.banned ? '封禁' : '解封'}、信誉分调整为${row.creditScore}、角色设为${row.role}？`,
    '用户治理操作',
    { type: 'warning' }
  )
  await userApi.adminUpdate(row.id, {
    banned: !!row.banned,
    creditScore: row.creditScore,
    role: row.role
  })
  ElMessage.success('用户信息已更新')
  searchUsers()
}

/* ---------- 数据看板 ---------- */
const overview = ref({})
const overviewColors = [
  'linear-gradient(135deg,#2F80ED,#56a3f5)',
  'linear-gradient(135deg,#FF8A65,#ffb199)',
  'linear-gradient(135deg,#52C41A,#95de64)',
  'linear-gradient(135deg,#722ED1,#b37feb)',
  'linear-gradient(135deg,#FA8C16,#ffc069)',
  'linear-gradient(135deg,#13C2C2,#5cdbd3)',
  'linear-gradient(135deg,#EB2F96,#ff85c0)',
  'linear-gradient(135deg,#FA541C,#ff9c6e)'
]
const LABEL_MAP = {
  userCount: '注册用户',
  users: '注册用户',
  postCount: '帖子总数',
  posts: '帖子总数',
  tripCount: '结伴行程',
  trips: '结伴行程',
  orderCount: '商城订单',
  orders: '商城订单',
  merchantCount: '入驻商家',
  merchants: '入驻商家',
  guideCount: '认证导游',
  guides: '认证导游',
  circleCount: '兴趣圈层',
  circles: '兴趣圈层',
  spotCount: '收录景点',
  spots: '收录景点',
  pendingCount: '待审内容',
  amount: '交易总额'
}
function overviewLabel(k) {
  return LABEL_MAP[k] || k.replace(/Count$/, '')
}

const pieEl = ref(null)
const barEl = ref(null)
const circleEl = ref(null)
const wordCloudData = ref([])
const charts = []

function makeChart(el) {
  const c = echarts.init(el)
  charts.push(c)
  return c
}

async function initStats() {
  const [ov, heat, circleAct, words] = await Promise.allSettled([
    statsApi.overview(),
    statsApi.spotHeat(),
    statsApi.circleActivity(),
    statsApi.hotWords()
  ])

  overview.value =
    ov.status === 'fulfilled' && ov.value && typeof ov.value === 'object'
      ? ov.value
      : {}

  await nextTick()

  // 分类饼图+热门景点TOP10柱状
  const heatData = heat.status === 'fulfilled' ? heat.value : {}
  const pieData = normalizeNameValue(heatData.categoryPie)
  if (pieEl.value) {
    makeChart(pieEl.value).setOption({
      title: { text: '景点分类占比', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, type: 'scroll' },
      series: [
        {
          type: 'pie',
          radius: ['38%', '62%'],
          center: ['50%', '46%'],
          data: pieData
        }
      ]
    })
  }

  const topSpots = heatData.topSpots || []
  if (barEl.value) {
    makeChart(barEl.value).setOption({
      title: { text: '热门景点 TOP10', left: 'center', textStyle: { fontSize: 14 } },
      tooltip: {},
      grid: { left: 70, right: 20, top: 40, bottom: 20 },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        inverse: true,
        data: topSpots.slice(0, 10).map((s) => s.name)
      },
      series: [
        {
          type: 'bar',
          data: topSpots.slice(0, 10).map((s) => s.heat ?? s.value ?? s.count ?? 0),
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#7cc4ff' },
              { offset: 1, color: '#2F80ED' }
            ]),
            borderRadius: [0, 6, 6, 0]
          },
          label: { show: true, position: 'right' }
        }
      ]
    })
  }

  // 圈层活跃度
  const circles = circleAct.status === 'fulfilled' ? circleAct.value : []
  if (circleEl.value) {
    makeChart(circleEl.value).setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['成员数', '帖子数'], top: 0 },
      grid: { left: 50, right: 30, top: 40, bottom: 60 },
      xAxis: {
        type: 'category',
        data: circles.map((c) => c.name),
        axisLabel: { interval: 0, rotate: 25 }
      },
      yAxis: { type: 'value' },
      series: [
        { name: '成员数', type: 'bar', data: circles.map((c) => c.memberCount), color: '#2F80ED' },
        { name: '帖子数', type: 'bar', data: circles.map((c) => c.postCount), color: '#FF8A65' }
      ]
    })
  }

  // 热词词云（自研组件，纯DOM布局）
  const wordData = words.status === 'fulfilled' ? words.value : []
  wordCloudData.value = normalizeNameValue(wordData)
}

function normalizeNameValue(arr) {
  if (!Array.isArray(arr)) return []
  return arr.map((x) =>
    typeof x === 'string'
      ? { name: x, value: 1 }
      : { name: x.name || x.keyword, value: x.value ?? x.count ?? 1 }
  )
}

let statsInited = false
async function onTabChange(name) {
  if (name === 'stats' && !statsInited) {
    statsInited = true
    await nextTick()
    setTimeout(initStats, 60)
  }
  if (name === 'settlement' && !settleInited) {
    settleInited = true
    loadSettlement()
  }
  if (name === 'complaints' && !complaintInited) {
    complaintInited = true
    await nextTick()
    setTimeout(() => loadComplaints(1), 60)
  }
}

/* ---------- 交易结算（管理员后台「结算」能力） ---------- */
const settleLoading = ref(false)
const settleKpis = ref({})
const settlement = ref({ shopRows: [], guideRows: [], records: [] })
let settleInited = false

const SETTLE_LABEL = {
  pendingPayable: '待结算总额',
  pendingCommission: '待结算平台佣金',
  paidTotal: '累计已发放',
  paidCount: '已发放笔数'
}

function settleKpiLabel(k) {
  const v = settleKpis.value[k]
  if (k === 'pendingPayable' || k === 'pendingCommission' || k === 'paidTotal') {
    return `${SETTLE_LABEL[k] || k}（¥${v}）`
  }
  return SETTLE_LABEL[k] || k
}

async function loadSettlement() {
  settleLoading.value = true
  try {
    const data = await settlementApi.overview()
    settlement.value = data || { shopRows: [], guideRows: [], records: [] }
    settleKpis.value = {
      pendingPayable: data?.pendingPayable ?? 0,
      pendingCommission: data?.pendingCommission ?? 0,
      paidTotal: data?.paidTotal ?? 0,
      paidCount: data?.paidCount ?? 0
    }
  } finally {
    settleLoading.value = false
  }
}

async function doPayout(row) {
  await ElMessageBox.confirm(
    `确认向「${row.targetName}」结算${row.orderCount} 笔订单，打款 ¥${row.payableAmount}？` +
      `平台留存佣金 ¥${row.commissionAmount}。`,
    '结算确认',
    { type: 'warning' }
  )
  await settlementApi.payout(row.type, row.targetId)
  ElMessage.success('结算完成，已生成发放记录')
  await loadSettlement()
}

function onResize() {
  charts.forEach((c) => c.resize())
}

onMounted(() => {
  loadPosts()
  loadReports()
  loadAds()
  loadAudit()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.two-chart {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}
@media (max-width: 900px) {
  .two-chart {
    grid-template-columns: 1fr;
  }
}
</style>
