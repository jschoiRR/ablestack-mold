// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

<template>
  <a-config-provider>
    <a-layout class="mold-mockup">
      <a-layout-sider class="mold-sider" :width="248" breakpoint="lg" :collapsed-width="0">
        <div class="mold-logo"><img :src="dark ? '/assets/white-logo-ablestack.png' : '/assets/logo-ablestack.png'" alt="ABLESTACK" /></div>
        <a-menu mode="inline" :selected-keys="['autoscale']" :open-keys="['compute']">
          <a-menu-item key="dashboard"><template #icon><dashboard-outlined /></template>대시보드</a-menu-item>
          <a-sub-menu key="compute"><template #icon><cloud-outlined /></template><template #title>컴퓨트</template>
            <a-menu-item key="vm"><template #icon><cloud-server-outlined /></template>가상머신</a-menu-item>
            <a-menu-item key="snapshot"><template #icon><camera-outlined /></template>VM 스냅샷</a-menu-item>
            <a-menu-item key="k8s"><template #icon><cluster-outlined /></template>쿠버네티스</a-menu-item>
            <a-menu-item key="autoscale"><template #icon><fullscreen-outlined /></template>오토스케일 VM 그룹</a-menu-item>
            <a-menu-item key="vmgroup"><template #icon><gold-outlined /></template>가상머신 그룹</a-menu-item>
            <a-menu-item key="ssh"><template #icon><key-outlined /></template>SSH 키 쌍</a-menu-item>
            <a-menu-item key="userdata"><template #icon><solution-outlined /></template>사용자 데이터 라이브러리</a-menu-item>
          </a-sub-menu>
          <a-sub-menu key="storage"><template #icon><hdd-outlined /></template><template #title>스토리지</template></a-sub-menu>
          <a-sub-menu key="network"><template #icon><wifi-outlined /></template><template #title>네트워크</template></a-sub-menu>
          <a-sub-menu key="image"><template #icon><picture-outlined /></template><template #title>이미지</template></a-sub-menu>
          <a-menu-item key="events"><template #icon><schedule-outlined /></template>이벤트</a-menu-item>
          <a-menu-item key="projects"><template #icon><project-outlined /></template>프로젝트</a-menu-item>
          <a-menu-item key="accounts"><template #icon><team-outlined /></template>계정</a-menu-item>
          <a-menu-item key="zones"><template #icon><global-outlined /></template>Zone</a-menu-item>
        </a-menu>
      </a-layout-sider>
      <a-layout>
        <a-layout-header class="mold-header">
          <menu-fold-outlined />
          <a-select value="AS-PROD-31-AS01-project" class="project-selector"><a-select-option value="AS-PROD-31-AS01-project">AS-PROD-31-AS01-project</a-select-option></a-select>
          <div class="header-user"><a-button type="primary">생성 <down-outlined /></a-button><translation-outlined /><bell-outlined /><a-avatar :size="28" style="background:#0969da">A</a-avatar><span>AS01 검증</span></div>
        </a-layout-header>
        <a-layout-content>
          <div class="mold-breadcrumb"><a-breadcrumb><a-breadcrumb-item><home-outlined /></a-breadcrumb-item><a-breadcrumb-item>오토스케일 VM 그룹</a-breadcrumb-item><a-breadcrumb-item>AS-PROD-31-AS04-CONCURRENCY</a-breadcrumb-item></a-breadcrumb><a-tag>목업</a-tag><div class="resource-actions"><a-button><template #icon><reload-outlined /></template>업데이트</a-button><a-button><template #icon><down-outlined /></template>작업</a-button></div></div>
          <div class="mold-resource"><resource-layout>
            <template #left><a-card class="mold-info-card">
              <div class="info-heading"><fullscreen-outlined /><h4>AS-PROD-31-AS04-CONCURRENCY</h4></div><a-divider />
              <dl class="info-list"><dt>상태</dt><dd><a-badge status="error" text="비활성화" /></dd><dt>아이디</dt><dd class="mono">bf0f5385-ae2c-4da4-aec4-eb985dbdeba4</dd><dt>조정 대상 VM · 범위</dt><dd>1 · 최소 1 · 최대 3</dd><dt>공인 → 게스트 포트</dt><dd>8084 → 8080</dd><dt>마지막 모니터 확인</dt><dd>2026. 10. 12. 02:30:00</dd><dt>연결된 네트워크</dt><dd><wifi-outlined /> AS-PROD-31-AS01-projectNetwork</dd><dt>공인 IP</dt><dd>10.10.31.104</dd><dt>프로젝트</dt><dd>AS-PROD-31-AS01-project</dd><dt>생성일</dt><dd>2026. 10. 11. 10:19:26</dd></dl>
            </a-card></template>
            <template #right><a-tabs class="mold-resource-tabs" v-model:active-key="active" :tab-position="mobile ? 'top' : 'left'" @change="selected = []; conditionSelected = []">
              <a-tab-pane v-for="tab in tabs" :key="tab.key" :tab="tab.label">
                <div class="mold-tab-content">
                  <a-space v-if="actionTabs.includes(tab.key)" class="tab-action-toolbar" :size="8" wrap aria-label="탭 액션">
                    <a-button v-for="action in actionsFor(tab.key)" :key="action.label" :type="action.primary ? 'primary' : 'default'" :disabled="isDisabled(action)" @click="runAction(action)"><template #icon><component :is="action.icon" /></template>{{ action.label }}</a-button>
                  </a-space>
                  <template v-if="tab.key === 'operations'">
                    <p class="helper-text">Cloud API를 통해 그룹·멤버·LB·이벤트를 각각 조회합니다. 출처별 조회 시각이 다를 수 있습니다.</p>
                    <a-descriptions size="small" :column="2" class="source-status"><a-descriptions-item v-for="label in ['그룹','멤버','LB 매핑','최근 이벤트']" :key="label" :label="label">2026. 10. 12. 02:30:00</a-descriptions-item></a-descriptions>
                    <a-card title="그룹 운영 요약" size="small" class="content-card"><p class="helper-text">실행 중은 VM 프로세스 상태이며 애플리케이션의 정상 응답을 보장하지 않습니다.</p><div class="status-grid"><div v-for="item in statusCounts" :key="item.label"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div></div><a-alert type="info" show-icon message="자동 정책 조정이 중지되어 있습니다. 이미 수락한 작업은 정리·복구를 계속할 수 있습니다." /></a-card>
                    <a-card title="최근 정책 평가" size="small" class="content-card"><a-alert type="info" show-icon message="비활성화 상태에서는 새 정책을 평가하지 않습니다. 아래는 마지막 평가 기록입니다." /><a-descriptions :column="2" size="small"><a-descriptions-item label="평가 결과">최소 멤버 수 유지</a-descriptions-item><a-descriptions-item label="평가 시각">2026. 10. 12. 02:25:00</a-descriptions-item></a-descriptions></a-card>
                    <a-card title="멤버 VM" size="small" class="content-card"><a-table :columns="vmColumns" :data-source="members" row-key="id" size="small" :pagination="false" /></a-card>
                  </template>
                  <template v-else-if="tab.key === 'profile'">
                    <a-alert type="info" show-icon message="프로필 변경은 이후 생성되는 VM에 적용됩니다. 기존 VM의 구성은 변경하지 않습니다." />
                    <a-card title="VM 프로필" size="small" class="content-card"><a-descriptions :column="1" size="small"><a-descriptions-item label="VM 삭제 유예 기간(초)">60</a-descriptions-item><a-descriptions-item label="템플릿">Ubuntu 24.04 LTS</a-descriptions-item><a-descriptions-item label="컴퓨트 오퍼링">AS-PROD-31-AS01-2C2G-Sparse</a-descriptions-item><a-descriptions-item label="사용자 데이터">web-init (목업 예시)</a-descriptions-item><a-descriptions-item label="적용 정책">ALLOWOVERRIDE</a-descriptions-item></a-descriptions></a-card>
                    <a-card title="배포 매개변수" size="small" class="content-card"><p class="helper-text">매개변수를 선택하면 삭제할 수 있습니다.</p><a-table :columns="paramColumns" :data-source="params" row-key="id" size="small" :pagination="false" :row-selection="selection" :scroll="{x:440}" /></a-card>
                  </template>
                  <template v-else-if="tab.key === 'lb'">
                    <a-card title="부하 분산 규칙" size="small" class="content-card"><a-descriptions :column="2" size="small"><a-descriptions-item label="이름">AS-PROD-31-AS04-LB</a-descriptions-item><a-descriptions-item label="제공자">VirtualRouter</a-descriptions-item><a-descriptions-item label="공인 IP">10.10.31.104</a-descriptions-item><a-descriptions-item label="포트">8084 → 8080</a-descriptions-item><a-descriptions-item label="프로토콜">TCP</a-descriptions-item><a-descriptions-item label="알고리즘">라운드 로빈</a-descriptions-item><a-descriptions-item label="고정 세션">미설정</a-descriptions-item><a-descriptions-item label="태그">purpose = autoscale</a-descriptions-item></a-descriptions></a-card>
                    <a-alert type="info" show-icon message="오토스케일 그룹이 사용하는 규칙은 이 탭에서 삭제할 수 없습니다." />
                    <a-card title="연결된 VM" size="small" class="content-card"><p class="helper-text">연결된 VM을 선택하면 기존 연결 해제 액션을 사용할 수 있습니다.</p><a-table :columns="vmColumns" :data-source="members" row-key="id" size="small" :pagination="false" :row-selection="selection" :scroll="{x:440}" /></a-card>
                  </template>
                  <template v-else-if="['up','down'].includes(tab.key)">
                    <div class="policy-selector"><span>정책 선택</span><a-select v-model:value="policy" style="width:240px"><a-select-option value="policy-0">{{ tab.key === 'up' ? 'ScaleUpPolicy-0' : 'ScaleDownPolicy-0' }}</a-select-option><a-select-option value="policy-1">{{ tab.key === 'up' ? 'ScaleUpPolicy-1' : 'ScaleDownPolicy-1' }}</a-select-option></a-select></div>
                    <a-card title="정책 설정" size="small" class="content-card"><a-descriptions :column="2" size="small"><a-descriptions-item label="이름">{{ policyLabel }}</a-descriptions-item><a-descriptions-item label="방향">{{ tab.key === 'up' ? '확장' : '축소' }}</a-descriptions-item><a-descriptions-item label="기간(초)">60</a-descriptions-item><a-descriptions-item label="재조정 대기 시간(초)">300</a-descriptions-item></a-descriptions><p class="helper-text">조건은 모두 충족해야 하며, 같은 방향의 정책 중 하나가 충족되면 조정을 요청합니다.</p></a-card>
                    <a-card title="조건" size="small" class="content-card"><p class="helper-text">조건을 선택하면 편집·삭제 버튼이 활성화됩니다.</p><a-table :columns="conditionColumns" :data-source="tab.key === 'up' ? upConditions : downConditions" row-key="id" size="small" :pagination="false" :row-selection="conditionSelection" :scroll="{x:440}" /></a-card>
                    <a-alert type="info" show-icon message="정책·조건 변경은 비활성화된 그룹에서만 가능합니다. 권한 및 공유 정책의 영향은 기존 검증을 유지합니다." />
                  </template>
                  <template v-else-if="tab.key === 'schedules'">
                    <a-alert type="info" show-icon message="그룹 변경 예약은 실행 시점의 상태·권한을 확인합니다. 비활성화된 그룹은 범위만 저장하며 자동 조정은 시작하지 않습니다." />
                    <a-card title="예약 목록" size="small" class="content-card"><p class="helper-text">예약을 선택하면 편집·삭제 버튼이 활성화됩니다.</p><a-table :columns="scheduleColumns" :data-source="schedules" row-key="id" size="small" :pagination="false" :row-selection="selection" :scroll="{x:620}" /><div class="pagination"><span>전체 3 개 항목 중 1-3 표시</span><a-pagination :total="3" :page-size="10" size="small" /></div></a-card>
                  </template>
                  <template v-else-if="tab.key === 'details'"><a-descriptions :column="1" size="small" title="상세"><a-descriptions-item label="이름">AS-PROD-31-AS04-CONCURRENCY</a-descriptions-item><a-descriptions-item label="아이디">bf0f5385-ae2c-4da4-aec4-eb985dbdeba4</a-descriptions-item><a-descriptions-item label="계정">AS-PROD-31-AS01-project</a-descriptions-item><a-descriptions-item label="최소 멤버 수">1</a-descriptions-item><a-descriptions-item label="최대 멤버 수">3</a-descriptions-item></a-descriptions></template>
                  <template v-else-if="tab.key === 'events'"><a-table :columns="eventColumns" :data-source="[]" :pagination="false" size="small" /></template>
                  <template v-else><a-textarea placeholder="코멘트 입력" :rows="4" /><div class="existing-comment-action"><a-button type="primary">추가</a-button></div></template>
                </div>
              </a-tab-pane>
            </a-tabs></template>
          </resource-layout></div>
        </a-layout-content>
      </a-layout>
    </a-layout>
    <a-modal v-model:visible="modal" :title="modalAction" centered :width="760" class="mold-dialog mockup-dialog" :mask-closable="false" @ok="modal = false" ok-text="확인" cancel-text="취소">
      <a-form layout="vertical"><a-form-item :label="modalAction.includes('조건') ? '카운터' : '이름'"><a-input :disabled="modalAction === '조건 편집'" :value="modalAction.includes('조건') ? 'VM CPU 평균 사용률' : 'ScaleUpPolicy-0'" /></a-form-item><a-form-item :label="modalAction.includes('조건') ? '관계 연산자' : '기간(초)'"><a-select v-if="modalAction.includes('조건')" value="GT"><a-select-option value="GT">초과</a-select-option><a-select-option value="LT">미만</a-select-option><a-select-option value="GE">이상</a-select-option><a-select-option value="LE">이하</a-select-option><a-select-option value="EQ">같음</a-select-option></a-select><a-input-number v-else :value="60" style="width:100%" /></a-form-item><a-form-item :label="modalAction.includes('조건') ? '임계값' : '재조정 대기 시간(초)'"><a-input-number :value="modalAction.includes('조건') ? 75 : 300" style="width:100%" /></a-form-item></a-form>
    </a-modal>
  </a-config-provider>
</template>
<script>
import ResourceLayout from '@/layouts/ResourceLayout.vue'
const params = new URLSearchParams(window.location.search)
const action = (label, icon, extra = {}) => ({ label, icon, ...extra })
export default {
  components: { ResourceLayout },
  data () {
    return {
      active: params.get('tab') || 'up', dark: params.get('theme') === 'dark', mobile: window.innerWidth < 768, selected: [], conditionSelected: [], policy: 'policy-0', modal: false, modalAction: '',
      actionTabs: ['operations','profile','lb','up','down','schedules'],
      tabs: [{key:'details',label:'상세'},{key:'operations',label:'운영 상태'},{key:'profile',label:'오토스케일 VM 프로필'},{key:'lb',label:'부하 분산 규칙'},{key:'up',label:'스케일업 정책'},{key:'down',label:'스케일다운 정책'},{key:'schedules',label:'스케줄'},{key:'events',label:'이벤트'},{key:'comments',label:'코멘트'}],
      statusCounts: [{label:'실행 중',value:1},{label:'전환 중',value:0},{label:'정지',value:0},{label:'오류·알 수 없음',value:0},{label:'삭제·정리 대기',value:0},{label:'기록된 멤버',value:1}],
      params: [{id:'networkids',name:'networkids',value:'AS-PROD-31-AS01-projectNetwork'},{id:'rootdisksize',name:'rootdisksize',value:'100 GiB'},{id:'diskofferingid',name:'diskofferingid',value:'GFS2 Sparse'}],
      paramColumns: [{title:'매개변수 이름',dataIndex:'name'},{title:'값',dataIndex:'value'}],
      upConditions: [{id:'cpu',counter:'VM CPU 평균 사용률',operator:'초과',threshold:'75 %'},{id:'memory',counter:'VM 메모리 평균 사용률',operator:'초과',threshold:'80 %'}],
      downConditions: [{id:'cpu',counter:'VM CPU 평균 사용률',operator:'미만',threshold:'20 %'},{id:'memory',counter:'VM 메모리 평균 사용률',operator:'미만',threshold:'30 %'}],
      conditionColumns: [{title:'카운터',dataIndex:'counter',width:230},{title:'관계 연산자',dataIndex:'operator',width:110},{title:'임계값',dataIndex:'threshold',width:100}],
      vmColumns: [{title:'가상머신 이름',dataIndex:'name'},{title:'Cloud VM 상태',dataIndex:'state'},{title:'LB 등록·적용 상태',dataIndex:'lb'}],
      members: [{id:'vm1',name:'i-35-397-VM',state:'실행 중',lb:'등록됨'}],
      schedules: [{id:'s1',description:'업무 시간 확장',action:'그룹 업데이트',range:'2 / 3',time:'월~금 09:00',enabled:'비활성화'},{id:'s2',description:'업무 종료 축소',action:'그룹 업데이트',range:'1 / 3',time:'월~금 18:00',enabled:'비활성화'},{id:'s3',description:'주말 최소 유지',action:'그룹 업데이트',range:'1 / 1',time:'토 00:00',enabled:'비활성화'}],
      scheduleColumns: [{title:'설명',dataIndex:'description',width:155},{title:'작업',dataIndex:'action',width:130},{title:'최소 / 최대',dataIndex:'range',width:100},{title:'예약 (Asia/Seoul)',dataIndex:'time',width:135},{title:'상태',dataIndex:'enabled',width:110}],
      eventColumns: ['레벨','유형','상태','사용자 이름','계정','도메인','생성일'].map((title,index)=>({title,key:index}))
    }
  },
  computed: {
    policyLabel () { return (this.active === 'up' ? 'ScaleUpPolicy-' : 'ScaleDownPolicy-') + (this.policy === 'policy-0' ? '0' : '1') },
    selection () { return {type:'radio',selectedRowKeys:this.selected,onChange:keys=>{this.selected=keys}} },
    conditionSelection () { return {type:'radio',selectedRowKeys:this.conditionSelected,onChange:keys=>{this.conditionSelected=keys}} }
  },
  created () { document.title = '#1355 Mold 액션 버튼 목업'; document.documentElement.classList.toggle('dark-mode',this.dark); document.body.classList.toggle('dark-mode',this.dark) },
  mounted () { window.addEventListener('resize',this.resize) },
  beforeUnmount () { window.removeEventListener('resize',this.resize) },
  methods: {
    resize () { this.mobile=window.innerWidth<768;this.$store.state.app.device=this.mobile?'mobile':'desktop' },
    actionsFor (tab) {
      const refresh=action('업데이트','ReloadOutlined',{refresh:true})
      if(tab==='operations')return [refresh]
      if(tab==='profile')return [action('프로필 편집','EditOutlined',{primary:true}),action('사용자 데이터 변경','SolutionOutlined'),action('매개변수 추가','PlusOutlined'),action('매개변수 삭제','DeleteOutlined',{selection:true}),refresh]
      if(tab==='lb')return [action('규칙 편집','EditOutlined',{primary:true}),action('태그 편집','TagOutlined'),action('고정 세션 설정','LinkOutlined'),action('규칙 삭제','DeleteOutlined',{blocked:true}),action('VM 연결 해제','DisconnectOutlined',{selection:true}),refresh]
      if(['up','down'].includes(tab))return [action('정책 추가','PlusOutlined',{primary:true}),action('정책 편집','EditOutlined'),action('정책 삭제','DeleteOutlined'),action('조건 추가','PlusOutlined'),action('조건 편집','EditOutlined',{condition:true}),action('조건 삭제','DeleteOutlined',{condition:true}),refresh]
      if(tab==='schedules')return [action('스케줄 추가','PlusOutlined',{primary:true}),action('스케줄 편집','EditOutlined',{selection:true}),action('스케줄 삭제','DeleteOutlined',{selection:true}),refresh]
      return []
    },
    isDisabled (a) { return a.blocked || (a.selection && !this.selected.length) || (a.condition && !this.conditionSelected.length) },
    runAction (a) { if(a.refresh || !['정책 추가','정책 편집','조건 추가','조건 편집'].includes(a.label))return;this.modalAction=a.label;this.modal=true }
  }
}
</script>
