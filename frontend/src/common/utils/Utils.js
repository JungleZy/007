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
 * Excel 文件合并成一个sheet
 * @param arr
 */
export const mergeExcelInfo = (arr) => {
  let all = {},
    newArr = [],
    len = 0,
    flag = false;
  arr.map((item, i) => {
    if (i === 0) {
      all = item
    } else {
      newArr.push(item);
    }
  });

  all.name = '全部';
  arr.map((item, j) => {
    if (j > 0) {
      item['data'] = window.luckysheet.transToData(item.celldata);
      if (j === 1) {
        all.config.borderInfo = [];
        all.config.columnlen = item.config.columnlen;
        all.config.customHeight = item.config.customHeight;
        all.config.customWidth = item.config.customWidth;
        all.config.merge = {};
        all.config.rowlen = {};
        all.celldata = [];
        all.data = []
      }
      for (let x in item.config.merge) {
        all.config.merge[(parseInt(item.config.merge[x].r) + len) + '_' + item.config.merge[x].c] = {
          r: parseInt(item.config.merge[x].r) + len,
          c: item.config.merge[x].c,
          rs: item.config.merge[x].rs,
          cs: item.config.merge[x].cs
        };
      }
      for (let y in item.config.rowlen) {
        all.config.rowlen[len + parseInt(y)] = item.config.rowlen[y];
      }
      for (let z in item.config.borderInfo) {
        all.config.borderInfo.push({
          rangeType: item.config.borderInfo[z].rangeType,
          value: {
            b: item.config.borderInfo[z].value.b,
            l: item.config.borderInfo[z].value.l,
            r: item.config.borderInfo[z].value.r,
            t: item.config.borderInfo[z].value.t,
            col_index: item.config.borderInfo[z].value.col_index,
            row_index: parseInt(item.config.borderInfo[z].value.row_index) + len,
          }
        });
      }
      for (let cell of item.celldata) {
        all.celldata.push({
          r: parseInt(cell.r) + len,
          c: cell.c,
          v: cell.v,
        });
      }

      if (item.data && item.data.length > 0) {
        item.data.map(row => {
          flag = false;
          for (let col of row) {
            if (col !== null) {
              flag = true;
            }
          }
          if (flag) {
            all.data.push(row);
          }
        });
      }

      len += parseInt(Object.keys(item.config.rowlen)[Object.keys(item.config.rowlen).length - 1]) + 1
    }
  });

  return all;
};

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


