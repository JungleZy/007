export function ImgError(app) {
  app.directive('real-img', {
    async mounted(el, b, v) {
      let imgURL = b.value;//获取图片地址
      if (imgURL) {
        let exist = await imageIsExist(imgURL);
        if (exist) {
          el.setAttribute('src', imgURL);
        }
      }
    }
  })
}

const imageIsExist = url => {
  return new Promise((resolve) => {
    let img = new Image();
    img.onload = function () {
      if (this.complete === true) {
        resolve(true);
        img = null;
      }
    }
    img.onerror = function () {
      resolve(false);
      img = null;
    }
    img.src = url;
  })
}