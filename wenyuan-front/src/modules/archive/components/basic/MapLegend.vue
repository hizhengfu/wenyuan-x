<template>
     <div class="lenged-box">
      <div>
        <img style="margin-right: 10px" width="25" height="34" src="../../assets/images/location1.png" alt=""><span>全国重点文物保护单位</span>
      </div>
      <div>
        <img style="margin-right: 10px" width="25" height="34" src="../../assets/images/location2.png" alt=""><span>省级文物保护单位</span>
      </div>
      <div>
        <img style="margin-right: 10px" width="25" height="34" src="../../assets/images/location3.png" alt=""><span>地(市)级文物保护单位</span>
      </div>
      <div>
        <img style="margin-right: 10px" width="25" height="34" src="../../assets/images/location4.png" alt=""><span>县(市)级文物保护单位</span>
      </div>
    </div>
</template>
<script lang="ts" setup>

</script>
<style lang="scss" scoped>
.lenged-box {
    width: 200px;
 
    background-color: #ffff;
    padding: 8px;
    border-radius: 4px;
    font-size: 14px;
    color: #333;
    box-shadow: 0px 0px 3px 2px rgba(0, 0, 0, 0.16);
    div {
      display: flex;
      align-items: center;
      span {
        width: 150px;
        text-align: left;
      }
    }
  }
</style>