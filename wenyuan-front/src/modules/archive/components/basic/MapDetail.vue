<template> 
 <n-modal
      v-bind="$attrs"
      preset="dialog"
      max-width="1000"
       style="width: 600px; position: fixed; right: 15%;  top: 10px"
    >
    <template #header>
      <div> {{ currentInfo.publishName }}</div>
    </template>
      <div class="matter-info">
        <!-- <div class="matter-info-title">
          <span>
            {{ currentInfo.publishName }}
          </span>
          
        </div> -->
        <div class="matter-info-middle">
          <div class="d-flex">
         
            <div class="matter-info-detail">
              <div class="matter-info-list">级别: <h-dict-text  v-model:value =currentInfo.publishLevel type="third_cultural_relic_level" ></h-dict-text></div>
              <div class="matter-info-list">年代: {{ currentInfo.publishYear || '--' }}</div>
              <div class="matter-info-list">类别: <h-dict-text  v-model:value =currentInfo.category type="third_cultural_relic_type" ></h-dict-text></div>
              <div class="matter-info-list">所属: {{ currentInfo.syqkLs || '--' }}</div>
              <div class="matter-info-list">省份: {{ currentInfo.areaName || '--' }}</div>
            </div>
          </div>
          <div v-if="currentInfo.currentAddress" class="matter-info-brief">
            <div style="font-weight: bold;">文物地址</div>
            <div class="line"></div>
            <div class="brief">
              <div class="brief-address">{{ currentInfo.currentAddress }}</div>
              <!-- <div class="brief-position">
                <span>经度: {{currentInfo.long}}</span>
                <span>纬度: {{currentInfo.lat}}</span>
              </div> -->
            </div>
          </div>
          <div v-if="currentInfo.jysm" class="matter-info-brief">
            <div style="font-weight: bold;">文物简介</div>
            <div class="line"></div>
            <div class="brief">
              {{ currentInfo.jysm }}
            </div>
          </div>
          <div v-if="currentInfo.bcxkXzzk" class="matter-info-brief">
            <div style="font-weight: bold;">保存现状</div>
            <div class="line"></div>
            <div class="brief">
              {{ currentInfo.bcxkXzzk }}
            </div>
          </div>
          <div v-if="currentInfo.csyyCsyyms" class="matter-info-brief">
            <div style="font-weight: bold;">残损原因</div>
            <div class="line"></div>
            <div class="brief">
              {{ currentInfo.csyyCsyyms }}
            </div>
          </div>
        
          <div v-if="currentInfo.remarks" class="matter-info-brief">
            <div style="font-weight: bold;">备注</div>
            <div class="line"></div>
            <div class="brief">
              {{ currentInfo.remarks }}
            </div>
          </div>
        </div>
      </div>
    </n-modal>
</template>
<script lang="ts" setup>
import { DefineProps } from 'vue';
const props = defineProps({
    currentInfo:{
        type: Object,
        default: () => ({})
    }
})
</script>
<style lang="scss" scoped>

.matter-info{
    width: 100%;
   height: 840px;
   display: flex;
   flex-direction: column;
}

.matter-info-title{
    width: 100%;
      height: 60px;
      font-size: 18px;
      background-color: #d3b35e;
      padding: 16px;
      color: #fff;
      display: flex;
      justify-content: space-between;
}
.matter-info-middle{
    padding: 16px;
      flex: 1;
      background: url('../../assets/images/model-bg.png') no-repeat center / 100% 100%;
      overflow: auto;
      flex: 1;
}
.d-flex {
    display: flex;

  }
.matter-info-detail {
        flex: 1;
        display: flex;
        flex-direction: column;
        color: #d3b35e;
}
.matter-info-list{
    margin-bottom: 22px;
          font-size: 18px;
}
.matter-info-brief {
        margin-top: 10px;   
    }
    .line {
          background-color: #d3b35e;
          height: 1px;
          margin-bottom: 20px;
          margin-top: 10px;
        }
        .brief {
          text-indent: 2rem;
          text-align: justify;
          display: flex;
          justify-content: space-between;}
          .brief-address{
            width: 70%;
          }
          .brief-position{
            width: 30%;
            display: flex;
            flex-direction: column;
          }
</style>