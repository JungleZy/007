import {onMounted, onUnmounted, ref} from "vue";

export default function (e) {
  let nodes;
  let offset = 97;
  let range = 26;
  let divId = document.getElementById(e);
  if (!!divId) {
    let char;
    let x = Math.floor(divId.offsetWidth / 36);
    let y = Math.floor(divId.offsetHeight / 36) + 1;
    let html = '';
    for (let i = 0; i < x * y; i++) {
      char = String.fromCharCode(offset + (Math.random() * range));
      html += '<div class="num-rain-div">' + char + '</div>'
    }
    divId.innerHTML = html;
    nodes = divId.getElementsByClassName('num-rain-div');
    function highlightChar(node, delay) {
      setTimeout(function () {
        node.setAttribute('data-state', 'active');
        setTimeout(function () {
          node.removeAttribute('data-state');
        }, 300);
      }, delay);
    }
    function highlightWord(word, characters) {
      let l = characters.length;
      for (let i = 0; i < l; i++) {
        let char = characters[i];
        if (word.indexOf(char.textContent) !== -1) {
          highlightChar(char, i);
        }
      }
    }
    let tens = ['zerop', 'teenq', 'twenty', 'thirty', 'fourty', 'fifty'];
    let units = ['zerop', 'oneq', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine', 'ten', 'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen'];
    function tick() {
      let text = '';
      let seconds = (new Date()).getSeconds();
      let unit = seconds % 10;
      let ten = Math.floor(seconds / 10);

      if (seconds <= 15) {
        text += units[seconds];
      } else if (seconds < 20) {
        text += units[unit] + tens[ten];
      } else {
        text += tens[ten] + (unit > 0 ? units[unit] : '');
      }
      highlightWord(text, nodes);
      run();
    }

    function run(){
      setTimeout(tick, 1000)
    }
    run()
  }
}