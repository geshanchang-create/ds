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

import { defineComponent } from 'vue'
import { useThemeStore } from '@/store/theme/theme'
import styles from './index.module.scss'
import LogoImage from '@/assets/images/LOGO.optimized.png'

const Logo = defineComponent({
  name: 'Logo',
  setup() {
    const themeStore = useThemeStore()

    return { themeStore }
  },
  render() {
    return (
      <div
        class={[
          styles.logo,
          styles[`logo-${this.themeStore.darkTheme ? 'dark' : 'light'}`]
        ]}
        style={{ 
          display: 'flex', 
          flexDirection: 'row', 
          alignItems: 'center', 
          gap: '8px',
          overflow: 'hidden',
          whiteSpace: 'nowrap',
          width: '260px' // Force fixed width so it doesn't recalculate layout during transition
        }}
      >
        <img 
          src={LogoImage} 
          alt="Logo" 
          style={{
            height: '36px',
            maxWidth: '120px',
            objectFit: 'contain',
            flexShrink: 0,
            transform: 'translateZ(0)', // Hardware acceleration
            willChange: 'transform',    // Tell browser not to repaint
            backfaceVisibility: 'hidden'
          }} 
        />
        <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', flexShrink: 0 }}>
          <span style="font-size: 14px; font-weight: bold; line-height: 1.3; letter-spacing: 0.5px; white-space: nowrap;">工业互联网故障检测</span>
          <span style="font-size: 14px; font-weight: bold; line-height: 1.3; letter-spacing: 0.5px; white-space: nowrap;">与质量管理平台</span>
        </div>
      </div>
    )
  }
})

export default Logo
