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

import { createApp } from 'vue'
import { createStore } from 'vuex'
import Antd from 'ant-design-vue'
import * as icons from '@ant-design/icons-vue'
import 'ant-design-vue/dist/antd.min.css'
import '@/style/theme/tokens.less'
import '@/style/theme/base.less'
import '@/style/theme/components.less'
import '@/style/theme/feedback.less'
import App from './App.vue'
import './mockup.less'

const app = createApp(App)
app.use(Antd)
app.use(createStore({ state: { app: { device: window.innerWidth < 768 ? 'mobile' : 'desktop' } } }))
Object.entries(icons).forEach(([name, component]) => app.component(name, component))
app.mount('#app')
