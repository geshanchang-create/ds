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

import { reactive, h } from 'vue'
import { NEllipsis, NIcon } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import {
  HomeOutlined,
  ProjectOutlined,
  BuildOutlined,
  BranchesOutlined,
  CarryOutOutlined,
  HddOutlined,
  FolderOpenOutlined,
  UnorderedListOutlined,
  ApiOutlined,
  DashboardOutlined,
  CloudServerOutlined,
  LineChartOutlined,
  LockOutlined,
  TeamOutlined,
  UserOutlined,
  BellOutlined,
  AlertOutlined,
  ToolOutlined,
  ApartmentOutlined,
  CompassOutlined,
  BlockOutlined,
  CloudOutlined,
  BarcodeOutlined,
  LogoutOutlined,
  KeyOutlined,
  InfoCircleOutlined
} from '@vicons/antd'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/user/user'
import { timezoneList } from '@/common/timezone'
import type { UserInfoRes } from '@/service/modules/users/types'

export function useDataList() {
  const { t } = useI18n()
  const route = useRoute()
  const userStore = useUserStore()

  const renderIcon = (icon: any) => {
    return () => h(NIcon, null, { default: () => h(icon) })
  }

  const localesOptions = [
    {
      label: 'English',
      key: 'en_US'
    },
    {
      label: '中文',
      key: 'zh_CN'
    }
  ]

  const timezoneOptions = () =>
    timezoneList.map((item) => ({ label: item, value: item }))

  const state = reactive({
    isShowSide: false,
    localesOptions,
    timezoneOptions: timezoneOptions(),
    userDropdownOptions: [],
    menuOptions: [],
    headerMenuOptions: [],
    sideMenuOptions: []
  })

  const changeMenuOption = (state: any) => {
    const projectCode = route.params.projectCode || ''
    const projectName = route.query.projectName || ''
    state.menuOptions = [
      {
        label: () => h(NEllipsis, null, { default: () => t('menu.home') }),
        key: 'home',
        icon: renderIcon(DashboardOutlined)
      },
      {
        label: () => h(NEllipsis, null, { default: () => t('menu.project') }),
        key: 'projects',
        icon: renderIcon(ApartmentOutlined),
        children: [
          {
            label: '项目列表',
            key: '/projects/list',
            icon: renderIcon(UnorderedListOutlined)
          },
          ...(projectCode ? [
            {
              label: t('menu.project') + (projectName ? `[${projectName}]` : ''),
              key: `/projects/${projectCode}`,
              icon: renderIcon(BuildOutlined),
              payload: { projectName: projectName },
              children: [
                {
                  label: t('menu.project_overview'),
                  key: `/projects/${projectCode}`,
                  payload: { projectName: projectName }
                },
                {
                  label: t('menu.project_parameter'),
                  key: `/projects/${projectCode}/parameter`,
                  payload: { projectName: projectName }
                },
                {
                  label: t('menu.project_preferences'),
                  key: `/projects/${projectCode}/preferences`,
                  payload: { projectName: projectName }
                }
              ]
            },
            {
              label: t('menu.workflow'),
              key: 'workflow',
              icon: renderIcon(BranchesOutlined),
              children: [
                {
                  label: t('menu.workflow_relation'),
                  key: `/projects/${projectCode}/workflow/relation`,
                  payload: { projectName: projectName }
                },
                {
                  label: t('menu.workflow_definition'),
                  key: `/projects/${projectCode}/workflow-definition`,
                  payload: { projectName: projectName }
                },
                {
                  label: t('menu.workflow_instance'),
                  key: `/projects/${projectCode}/workflow/instances`,
                  payload: { projectName: projectName }
                },
                {
                  label: t('menu.workflow_timing'),
                  key: `/projects/${projectCode}/workflow/timings`,
                  payload: { projectName: projectName }
                }
              ]
            },
            {
              label: t('menu.task'),
              key: 'task',
              icon: renderIcon(CarryOutOutlined),
              children: [
                {
                  label: t('menu.task_instance'),
                  key: `/projects/${projectCode}/task/instances`,
                  payload: { projectName: projectName }
                }
              ]
            }
          ] : [])
        ]
      },
      {
        label: () => h(NEllipsis, null, { default: () => t('menu.resources') }),
        key: 'resource',
        icon: renderIcon(HddOutlined),
        children: [
          {
            label: t('menu.file_manage'),
            key: '/resource/file-manage',
            icon: renderIcon(FolderOpenOutlined)
          },
          {
            label: t('menu.task_group_manage'),
            key: 'task-group-manage',
            icon: renderIcon(UnorderedListOutlined),
            children: [
              {
                label: t('menu.task_group_option'),
                key: '/resource/task-group-option'
              },
              {
                label: t('menu.task_group_queue'),
                key: '/resource/task-group-queue'
              }
            ]
          }
        ]
      },
      {
        label: () =>
          h(NEllipsis, null, { default: () => t('menu.datasource') }),
        key: 'datasource',
        icon: renderIcon(ApiOutlined)
      },
      {
        label: () => h(NEllipsis, null, { default: () => t('menu.monitor') }),
        key: 'monitor',
        icon: renderIcon(LineChartOutlined),
        children: [
          {
            label: t('menu.service_manage'),
            key: 'service-manage',
            icon: renderIcon(CloudServerOutlined),
            children: [
              {
                label: t('menu.master'),
                key: '/monitor/master'
              },
              {
                label: t('menu.worker'),
                key: '/monitor/worker'
              },
              {
                label: t('menu.alert_server'),
                key: '/monitor/alert_server'
              },
              {
                label: t('menu.db'),
                key: '/monitor/db'
              }
            ]
          },
          {
            label: t('menu.statistical_manage'),
            key: 'statistical-manage',
            icon: renderIcon(LineChartOutlined),
            children: [
              {
                label: t('menu.statistics'),
                key: '/monitor/statistics'
              },
              {
                label: t('menu.audit_log'),
                key: '/monitor/audit-log'
              }
            ]
          }
        ]
      },
      {
        label: () => h(NEllipsis, null, { default: () => t('menu.security') }),
        key: 'security',
        icon: renderIcon(KeyOutlined),
        children:
          (userStore.getUserInfo as UserInfoRes).userType === 'ADMIN_USER'
            ? [
                {
                  label: t('menu.tenant_manage'),
                  key: '/security/tenant-manage',
                  icon: renderIcon(TeamOutlined)
                },
                {
                  label: t('menu.user_manage'),
                  key: '/security/user-manage',
                  icon: renderIcon(UserOutlined)
                },
                {
                  label: t('menu.alarm_group_manage'),
                  key: '/security/alarm-group-manage',
                  icon: renderIcon(BellOutlined)
                },
                {
                  label: t('menu.alarm_instance_manage'),
                  key: '/security/alarm-instance-manage',
                  icon: renderIcon(AlertOutlined)
                },
                {
                  label: t('menu.worker_group_manage'),
                  key: '/security/worker-group-manage',
                  icon: renderIcon(ToolOutlined)
                },
                {
                  label: t('menu.yarn_queue_manage'),
                  key: '/security/yarn-queue-manage',
                  icon: renderIcon(ApartmentOutlined)
                },
                {
                  label: t('menu.environment_manage'),
                  key: '/security/environment-manage',
                  icon: renderIcon(CompassOutlined)
                },
                {
                  label: t('menu.cluster_manage'),
                  key: '/security/cluster-manage',
                  icon: renderIcon(BlockOutlined)
                },
                {
                  label: t('menu.k8s_namespace_manage'),
                  key: '/security/k8s-namespace-manage',
                  icon: renderIcon(CloudOutlined)
                },
                {
                  label: t('menu.token_manage'),
                  key: '/security/token-manage',
                  icon: renderIcon(BarcodeOutlined)
                }
              ]
            : [
                {
                  label: t('menu.token_manage'),
                  key: '/security/token-manage',
                  icon: renderIcon(BarcodeOutlined)
                }
              ]
      }
    ]
  }

  const changeHeaderMenuOptions = (state: any) => {
    state.headerMenuOptions = state.menuOptions.map(
      (item: { label: string; key: string; icon: any }) => {
        return {
          label: item.label,
          key: item.key,
          icon: item.icon
        }
      }
    )
  }

  const changeUserDropdown = (state: any) => {
    state.userDropdownOptions = [
      {
        label: t('user_dropdown.profile'),
        key: 'profile',
        icon: renderIcon(UserOutlined)
      },
      {
        label: t('user_dropdown.password'),
        key: 'password',
        icon: renderIcon(KeyOutlined),
        disabled: userStore.getSecurityConfigType !== 'PASSWORD'
      },
      {
        label: t('user_dropdown.about'),
        key: 'about',
        icon: renderIcon(InfoCircleOutlined)
      },
      {
        label: t('user_dropdown.logout'),
        key: 'logout',
        icon: renderIcon(LogoutOutlined)
      }
    ]
  }

  return {
    state,
    changeHeaderMenuOptions,
    changeMenuOption,
    changeUserDropdown
  }
}
