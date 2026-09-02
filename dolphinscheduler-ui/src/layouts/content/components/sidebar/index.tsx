/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { defineComponent, ref, PropType } from 'vue'
import { NLayoutSider, NMenu, NButton, NIcon, NPopover } from 'naive-ui'
import { useMenuClick } from './use-menuClick'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SettingOutlined } from '@vicons/antd'
import Logo from '../logo'
import Locales from '../locales'
import Theme from '../theme'
import User from '../user'

const Sidebar = defineComponent({
  name: 'Sidebar',
  props: {
    sideMenuOptions: {
      type: Array as PropType<any>,
      default: []
    },
    sideKey: {
      type: String as PropType<string>,
      default: ''
    },
    localesOptions: {
      type: Array as PropType<any>,
      default: []
    },
    timezoneOptions: {
      type: Array as PropType<any>,
      default: []
    },
    userDropdownOptions: {
      type: Array as PropType<any>,
      default: []
    }
  },
  setup() {
    const collapsedRef = ref(false)
    const router = useRouter()
    const { t } = useI18n()
    const defaultExpandedKeys = [
      'workflow',
      'task',
      'service-manage',
      'statistical-manage',
      'task-group-manage'
    ]
    const expandedKeys = ref<string[]>(defaultExpandedKeys)

    const { handleMenuClick } = useMenuClick()

    const handleUISettingClick = () => {
      router.push({ path: '/ui-setting' })
    }

    const handleExpandedKeysChange = (keys: string[]) => {
      expandedKeys.value = keys
    }

    const handleCollapse = () => {
      collapsedRef.value = true
      expandedKeys.value = [] // Fold all submenus when sidebar collapses
    }

    const handleExpand = () => {
      collapsedRef.value = false
    }

    return {
      collapsedRef,
      expandedKeys,
      handleExpandedKeysChange,
      handleCollapse,
      handleExpand,
      handleMenuClick,
      handleUISettingClick,
      t
    }
  },
  render() {
    return (
      <NLayoutSider
        bordered
        nativeScrollbar={false}
        show-trigger='bar'
        collapse-mode='width'
        collapsed={this.collapsedRef}
        onCollapse={this.handleCollapse}
        onExpand={this.handleExpand}
        width={260}
      >
        <div style="display: flex; flex-direction: column; height: 100%;">
          <div style="flex: 0 0 65px; display: flex; align-items: center; border-bottom: 1px solid rgba(0,0,0,0.03); overflow: hidden;">
            {!this.collapsedRef && <Logo />}
          </div>

          <div style="flex: 1; overflow-y: auto;">
            <NMenu
              class='tab-vertical'
              value={this.sideKey}
              options={this.sideMenuOptions}
              expandedKeys={this.expandedKeys}
              onUpdateExpandedKeys={this.handleExpandedKeysChange}
              onUpdateValue={this.handleMenuClick}
            />
          </div>

          <div class="sidebar-bottom-dock" style={{
            flex: '0 0 auto',
            padding: this.collapsedRef ? '12px 4px' : '16px 20px',
            display: 'flex',
            flexDirection: this.collapsedRef ? 'column' : 'row',
            gap: '8px',
            borderTop: '1px solid rgba(0,0,0,0.03)',
            alignItems: 'center',
            justifyContent: 'space-between'
          }}>
            <User userDropdownOptions={this.userDropdownOptions} />
            <NButton quaternary onClick={this.handleUISettingClick} title={this.t('menu.ui_setting')}>
              {{
                icon: () => (
                  <NIcon size='16'>
                    <SettingOutlined />
                  </NIcon>
                )
              }}
            </NButton>
            <Theme />
            <Locales localesOptions={this.localesOptions} />
          </div>
        </div>
      </NLayoutSider>
    )
  }
})

export default Sidebar
