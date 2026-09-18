import {ref} from "vue";
export function parseIdCard(idCard, type) {
  if (type === 1) {
    return idCard.substring(6, 10) + "-" + idCard.substring(10, 12) + "-" + idCard.substring(12, 14);
  }
  if (type === 2) {
    if (parseInt(idCard.substr(16, 1)) % 2 === 1) {
      return 1;
    } else {
      return 0
    }
  }
}

/**
 * 时间格式转换 YYYY-MM-DD hh:mm:ss
 * @param time
 * @param type
 */
export const timeFormatInfo = (time, type) => {
  let Y = new Date(time).getFullYear(),
    M = new Date(time).getMonth() + 1,
    D = new Date(time).getDate(),
    h = new Date(time).getHours(),
    m = new Date(time).getMinutes(),
    s = new Date(time).getSeconds();

  M = M > 9 ? (M + '') : ('0' + M);
  D = D > 9 ? (D + '') : ('0' + D);
  h = h > 9 ? (h + '') : ('0' + h);
  m = m > 9 ? (m + '') : ('0' + m);
  s = s > 9 ? (s + '') : ('0' + s);

  if (type === 'string') {
    return Y + M + D + h + m + s;
  } else {
    return Y + '-' + M + '-' + D + ' ' + h + ':' + m + ':' + s;
  }
};

/**
 * 时间格式转换 hh：mm：ss
 * @param t
 * @param type
 */
export const partTimeFormatInfo = (t, type) => {
  let h, m, s;
  h = Math.floor(t / 1000 / 60 / 60 % 24);
  m = Math.floor(t / 1000 / 60 % 60);
  s = Math.floor(t / 1000 % 60);

  if (type === 'number') {
    h = h > 9 ? h + "" : '0' + h;
    m = m > 9 ? m + "" : '0' + m;
    s = s > 9 ? s + "" : '0' + s;
    return h + '：' + m + '：' + s;
  } else if (type === 'chinese') {
    h = h > 0 ? h + "时" : '';
    m = m > 0 ? m + "分" : '';
    s = s > 0 ? s + "秒" : '';
    return h + m + s;
  } else {
    return '';
  }
};

/**
 * 数组求和
 * @param arr
 */
export const sum = (arr) => {
  let num = 0
  arr.forEach(item=>{
    num+=item
  })
  return num
  // return eval(arr.join("+"));
};

/**
 * 对象深拷贝
 * @param obj
 * @returns {{}}
 */
export const deepClone = (obj = {}) => {
  let newObj = null
  if (typeof (obj) == 'object' && obj !== null) {
    newObj = obj instanceof Array ? [] : {}
    for (let i in obj) {
      newObj[i] = deepClone(obj[i])
    }
  } else {
    newObj = obj
  }
  return newObj
}

/**
 * 字体大小配置
 */
export const fontSizeDispose = () => {
  const list = ref(document.querySelectorAll('.fs_dispose'));
  const list_min = ref(document.querySelectorAll('.fs_dispose_min'));
  const list_1 = ref(document.querySelectorAll('.fs_dispose_1'));
  let fs = window.localStorage.getItem('fs'),
    _fs = 0,boxStyle = null;
  for(let box of list.value) {
    if (box.getAttribute('fs') !== 'dis') {
      _fs = parseInt(getComputedStyle(box, null)['fontSize']);
      boxStyle = box.style;
      boxStyle.setProperty('font-size', (_fs + fs * 2) + 'px', 'important');
      box.setAttribute('fs', 'dis');
    }
  }
  for(let box1 of list_1.value) {
    if (box1.getAttribute('fs') !== 'dis') {
      _fs = parseInt(getComputedStyle(box1, null)['fontSize']);
      boxStyle = box1.style;
      boxStyle.setProperty('font-size', (_fs + fs * 1) + 'px', 'important');
      box1.setAttribute('fs', 'dis');
    }
  }
  for(let _box of list_min.value) {
    if (_box.getAttribute('fs') !== 'dis') {
      _fs = parseInt(getComputedStyle(_box, null)['fontSize']);
      boxStyle = _box.style;
      boxStyle.setProperty('font-size', (_fs - fs * 1) + 'px', 'important')
      _box.setAttribute('fs', 'dis');
    }
  }
}

/**
 * 按 path 前缀解析同级菜单路由。
 *
 * 菜单 path 带皮肤后缀（equipmentScoreHJJ / equipmentListHJJ），硬编码裸名拼路径会跳 404；
 * 且 config/router/guards.js 的 nestedPatDown 会在导航中 splice 掉 TransitionPage 层，
 * route.matched 的下标语义不稳定，所以这里不按下标取父级，而是在 matched 链上找
 * 「真正拥有该前缀子节点」的那一层。
 *
 * @param route useRoute() 返回的当前路由
 * @param prefix 目标菜单 path 前缀，如 'equipmentScore'
 * @returns {string|null} 可直接 push 的绝对路径；菜单里没有该项时返回 null
 */
export const resolveSiblingPath = (route, prefix) => {
  for (const record of route.matched) {
    const child = (record.children || []).find(c => typeof c.path === 'string' && c.path.startsWith(prefix))
    if (child) return record.path + '/' + child.path
  }
  return null
}
