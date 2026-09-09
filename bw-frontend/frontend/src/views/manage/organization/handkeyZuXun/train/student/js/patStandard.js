import {sum} from "../../../../../../../common/utils/Utils";

export default function () {

  /**
   * 根据拍发记录计算拍发基准值
   * @param arr - 拍发记录列表
   * @param patStandard - 原始拍发基准值
   */
  const countPatStandardInfo = (arr,patStandard,initFloat) => {
    let d_diff = [],l_diff = [],c_gap = [],w_gap = [],g_gap = [];
    arr.map(item => {
      if (item.code === 0) {
        d_diff.push(parseInt(item.diff));
      }
      if (item.code === 1 && parseInt(item.diff) <= patStandard.value.line*3) {
        l_diff.push(parseInt(item.diff));
      }
      if (parseInt(item.gap) > 10) {
        if (parseInt(item.gap) <= patStandard.value.codeGap*(1 + initFloat.value/100)) {
          c_gap.push(parseInt(item.gap));
        } else if (parseInt(item.gap) <= patStandard.value.wordGap*(1 + initFloat.value/100)) {
          w_gap.push(parseInt(item.gap));
        } else if (parseInt(item.gap) <= patStandard.value.groupGap*3) {
          g_gap.push(parseInt(item.gap));
        }
      }
    });
    if (d_diff.length > 0) {
      patStandard.value.dot = parseInt(sum(d_diff)/d_diff.length);
    }
    if (l_diff.length > 0) {
      patStandard.value.line = parseInt(sum(l_diff)/l_diff.length);
    }
    if (c_gap.length > 0) {
      patStandard.value.codeGap = parseInt(sum(c_gap)/c_gap.length);
    }
    if (patStandard.value.codeGap < patStandard.value.dot) {
      patStandard.value.codeGap = patStandard.value.dot
    }
    if (patStandard.value.codeGap < 60) {
      patStandard.value.codeGap = 60
    }
    // if (w_gap.length > 0) {
    //   patStandard.value.wordGap = parseInt(sum(w_gap)/w_gap.length);
    patStandard.value.wordGap = parseInt(patStandard.value.codeGap * 3);
    // }
    // if (g_gap.length > 0) {
    //   patStandard.value.groupGap = parseInt(sum(g_gap)/g_gap.length);
    patStandard.value.groupGap = parseInt(patStandard.value.codeGap * 5);
    // }
    // console.log(patStandard.value)
    return patStandard.value
  }

  const countAverageStandard = (oldPatStandard,pagePatStandard,patStandard) => {

    let d_diff = [],l_diff = [],c_gap = [],w_gap = [],g_gap = [];
    pagePatStandard.value.map(item => {
      d_diff.push(parseInt(item.dot));
      l_diff.push(parseInt(item.line));
      c_gap.push(parseInt(item.codeGap));
      w_gap.push(parseInt(item.wordGap));
      g_gap.push(parseInt(item.groupGap));
    });

    if (d_diff.length > 0) {
      patStandard.value.dot = parseInt((sum(d_diff)/d_diff.length+oldPatStandard.value.dot)/2);
    }
    if (l_diff.length > 0) {
      patStandard.value.line = parseInt((sum(l_diff)/l_diff.length+oldPatStandard.value.line)/2);
    }
    if (c_gap.length > 0) {
      patStandard.value.codeGap = parseInt((sum(c_gap)/c_gap.length+oldPatStandard.value.c_gap)/2);
    }
    if (w_gap.length > 0) {
      patStandard.value.wordGap = parseInt((sum(w_gap)/w_gap.length+oldPatStandard.value.w_gap)/2);
    }
    if (g_gap.length > 0) {
      patStandard.value.groupGap = parseInt((sum(g_gap)/g_gap.length+oldPatStandard.value.g_gap)/2);
    }

    return patStandard.value
  }


  return {
    countPatStandardInfo,
    countAverageStandard
  }
}