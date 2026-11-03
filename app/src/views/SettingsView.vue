<script setup>
import { useRouter } from 'vue-router'
import BrandLogo from '@/components/BrandLogo.vue'
import ModelConnections from '@/components/ModelConnections.vue'
import '@/styles/v2.css'
import '@/styles/readability.css'
const router = useRouter()
</script>
<template>
 <div class="v2-auth">
  <header class="settings-navigation"><BrandLogo /><button class="btn btn-quiet back-icon" aria-label="返回对话" @click="router.push('/v2')">&lt;</button></header>
  <main class="v2-auth-main"><section class="v2-auth-card v2-settings-card"><h1 class="v2-auth-title">连接 AI</h1><ModelConnections /></section></main>
 </div>
</template>
<style scoped>.settings-navigation{display:flex;flex-direction:column;align-items:flex-start;gap:14px;padding:24px 32px}.back-icon{font-size:28px;min-width:44px;padding:2px 10px}.v2-auth-main{display:block;min-height:0;width:100%;padding:24px 24px 48px}.v2-settings-card{margin:0 auto}</style>
