<template>
  <div class="search-container">
    <div v-show="isSearchShow" class="search-box">
      <div class="sea-box">
        <div v-if="isAdmin" class="input-box-wrap">
          <div class="search-wrap"></div>
          <div class="input-box-wrap2">
            <div class="input-box">
              <input
                placeholder="请输入关键词"
                class="filter-search"
                v-model="params.publishName"
                label="请输入名称"
                type="text"
              />
              <n-icon
                v-if="params.publishName"
                @click="params.publishName = ''"
                size="24px"
                style="cursor: pointer; margin-right: 5px"
                color="#a51a1a"
              >
                <icon-mdi-close></icon-mdi-close>
              </n-icon>
              <n-icon style="cursor: pointer" color="#a51a1a" size="24px">
                <icon-mdi-magnify></icon-mdi-magnify>
              </n-icon>
            </div>
            <div @click="inputSearch" class="back-btn2" style="background: #a51a1a; color: #fff; cursor: pointer">
              确定
            </div>
          </div>

          <div class="filter-detail">
            <div class="filter-detail-item">
              <div class="filter-detail-title">
                <span>级别</span>
                <span @click="clickTag('level', '')" class="fil-reset">重置</span>
              </div>
              <div>
                <n-space>
                  <n-tag v-for="(tag, index) in culRank" :key="index" @click="clickTag('level', tag)" :type="tag.type">
                    {{ tag.value }}
                  </n-tag>
                </n-space>
              </div>
            </div>
            <div class="filter-detail-item">
              <div class="filter-detail-title">
                <span>类别</span>
                <span @click="clickTag('category', '')" class="fil-reset">重置</span>
              </div>
              <div>
                <n-space>
                  <n-tag
                    v-for="(tag, index) in culCategory"
                    :key="index"
                    @click="clickTag('category', tag)"
                    :type="tag.type"
                    >{{ tag.value }}
                  </n-tag>
                </n-space>
                <!-- <v-chip-group
                  column
                  multiple
                  v-model="amen2"
                >
                  <v-chip
                    @click="chipClick('category', tag)"
                    active-class="active"
                    outlined
                    label
                    color="red"
                    text-color="#999"
                    v-for="(tag, index) in culCategory"
                    :key="index"
                  >
                    {{ tag.label }}
                  </v-chip>
                </v-chip-group> -->
              </div>
            </div>
            <!-- <div class="filter-detail-item">
              <div class="filter-detail-title">
                <span>类别明细</span>
                <span @click="clickTag('categoryDetail','')" class="fil-reset">重置</span>
              </div>
              <div>
                  <n-space v-for="(item,index1) in culCategory" :key='index1' >
                      <n-tag v-for="(tag,index) in culCategory" :key="index" @click="clickTag('category',tag)" :type="tag.type" >{{tag.value}}</n-tag>
                  </n-space>

              </div>
            </div> -->
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useAuthStore } from '@/store';
import { type } from '@/modules/sys/api/dict';

const authStore = useAuthStore();
const isSearchShow = ref(true);
const activeLevel: any = ref([]);
const activeCategory: any = ref([]);
const activeCategoryDetail: any = ref([]);
const culRank: any = ref([]);
const culCategory: any = ref([]);
const categoryDetail: any = ref([]);
const params = ref({
  publishName: '',
  areaId: '',

  publishLevel: '',
  category: '',
});
const emit = defineEmits(['search']);
const isAdmin = computed(() => {
  if (authStore.roles?.indexOf('1') !== -1) {
    return true;
  } else {
    return false;
  }
});
const props = defineProps({
  searchParams: {
    type: Object,
    default: () => {},
  },
});

function clickTag(flag: any, item: any) {
  let activeArray;
  let setActiveArray;
  let targetRankArray;

  switch (flag) {
    case 'level':
      activeArray = activeLevel;
      setActiveArray = (newValue: any) => (activeLevel.value = newValue);
      targetRankArray = culRank.value;
      break;
    case 'category':
      activeArray = activeCategory;
      setActiveArray = (newValue: any) => (activeCategory.value = newValue);
      targetRankArray = culCategory.value;
      break;
    case 'categoryDetail':
      activeArray = activeCategoryDetail;
      setActiveArray = (newValue: any) => (activeCategoryDetail.value = newValue);
      targetRankArray = categoryDetail.value;
      break;
    default:
      return;
  }
  if (!item) {
    setActiveArray([]);
    if (targetRankArray) {
      targetRankArray.forEach((item: any) => {
        item.type = 'default';
      });
    }
    return;
  }

  const index = activeArray.value.indexOf(item.key);

  if (index === -1) {
    setActiveArray([...activeArray.value, item.key]);
    item.type = 'error';
  } else {
    const newActiveArray = [...activeArray.value];
    newActiveArray.splice(index, 1);
    setActiveArray(newActiveArray);
    item.type = 'default';
  }
}

function inputSearch() {
  params.value.areaId = props.searchParams.areaId;
  params.value.category = activeCategory.value.join(',');
  params.value.publishLevel = activeLevel.value.join(',');
  if (params.value.publishName) {
    emit('search', params.value);
  } else {
    emit('search', { ...params.value, publishName: '' });
  }
}

onMounted(() => {
  //获取字典数据
  type('third_cultural_relic_level').then((resp: any) => {
    let data = [];
    if (resp.data) {
      data = Object.entries(resp.data).map(([key, value]) => ({
        key: parseInt(key),
        value: value,
      }));
      culRank.value = data.map((tag: any) => ({
        ...tag,
        type: 'default',
      }));
    }
  });
  type('third_cultural_relic_type').then((resp: any) => {
    if (resp.data) {
      culCategory.value = Object.entries(resp.data).map(([key, value]) => ({
        key: parseInt(key),
        value: value,
      }));
    }
  });
});
watch(
  () => activeLevel.value,
  (newValue) => {
    console.log(newValue, '---888');

    newValue.map((item: any, index: any) => {
      console.log(item, '-----99');
    });
    if (newValue) {
      let publishLevel = newValue.join(',');
      let mainCategory = activeCategory.value.join(',');
      params.value.category = mainCategory;
      params.value.areaId = props.searchParams.areaId;
      params.value.publishLevel = publishLevel;
      console.log(publishLevel, '----');

      emit('search', params.value);
    }
  },
  { deep: true },
);
watch(
  () => activeCategory.value,
  (newValue) => {
    if (newValue) {
      let mainCategory = newValue.join(',');
      let publishLevel = activeLevel.value.join(',');
      params.value.category = mainCategory;
      params.value.areaId = props.searchParams.areaId;
      params.value.publishLevel = publishLevel;
      emit('search', params.value);
    }
  },
  { deep: true },
);
</script>
<style lang="scss" scoped>
.search-container {
  // margin: 10px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.back-btn {
  width: 66px;
  padding: 8px 16px;
  margin-bottom: 10px;
  align-items: center;
  background-color: #fff;
  color: #333;
  border-radius: 4px;
  cursor: pointer;
  box-shadow: 0px 0px 3px 2px rgba(0, 0, 0, 0.16);
  display: flex;
  align-items: center;
}

.search-box {
  width: auto;
  border-radius: 4px;

  .search-wrap {
    display: flex;
    // margin-bottom: 16px;
    .search-item {
      width: 190px;
      height: 40px;
      margin-right: 5px;
      border-radius: 4px;
      border: solid 1px rgba(51, 51, 51, 0.3);
      overflow: hidden;
    }
  }

  .input-box-wrap {
    padding: 16px 10px;
    background-color: #fff;
    border-radius: 4px;
    margin-bottom: 14px;
    color: #333;
    box-shadow: 0px 0px 3px 2px rgba(0, 0, 0, 0.16);
  }

  .input-box {
    height: 40px;
    display: flex;
    flex: 1;
    margin-right: 12px;
    padding: 7px 10px;
    border: solid 1px rgba(51, 51, 51, 0.3);
    background-color: #fff;
    border-radius: 4px;
  }

  .sea-box {
    display: flex;
    flex-direction: column;
    color: #333;

    .filter-btn {
      cursor: pointer;
      padding-left: 10px;
    }

    .filter-search {
      width: 95%;
      color: #333;
      outline: none;
    }

    .filter-search::placeholder {
      color: #999;
    }
  }

  .filter-detail {
    margin-top: 10px;
    width: 100%;
    border-radius: 4px;
    background-color: #ffff;
    // padding: 16px 16px;
    // box-shadow: 0px 0px 3px 2px rgba(0, 0, 0, 0.16);
    .filter-detail-item {
      .filter-detail-title {
        color: #333;
        font-size: 18px;
        margin-bottom: 5px;
        border-bottom: 1px solid #d1d1d1;
        display: flex;
        justify-content: space-between;
        align-items: center;

        .fil-reset {
          cursor: pointer;
          color: #333;
          font-size: 16px;
        }

        > span:first-child::before {
          content: '';
          display: inline-block;
          width: 4px;
          height: 14px;
          background-color: #a51a1a;
          border-radius: 7px;
          margin-right: 5px;
        }
      }

      .active {
        border: solid 1px #a51a1a !important;
        color: #333 !important;
        background: #7aa7cf00 !important;
      }
    }
  }
}

.input-box-wrap2 {
  display: flex;
}

.back-btn2 {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  border-radius: 8px;
}

:deep(input) {
  border-style: none;
}
</style>
