
export default class WB_ColorSHow {
  constructor(k) {
    this.d_h = "string" === typeof k ? document.getElementById(k) : k;
    this.d_B = this.d_h.getContext("2d");
    // this.BackgroundColor = "rgba(24,45, 86)";
    this.BackgroundColor = "rgba(0,0,0,0)";
    // this.BackgroundColor = "#fff";
    this.FillColor1 = "#ffaa03";
    this.FillColor2 = "#03fcb3";
    this.FillColor3 = "#ec1031";
    this.FillColor4 = "#b266ff";
    this.type = "CZ"
    this.init = function() {
      this.d_B.clearRect(0, 0, this.d_h.width, this.d_h.height)
      this.d_B.fillStyle = this.BackgroundColor;
      this.d_B.fillRect(0, 0, this.d_h.width, this.d_h.height)
    }
    ;
    this.d_AE = function(a) {
      "" != a && (this.d_B.font = "200px " + unescape("%u5B8B%u4F53"),
          this.d_B.textBaseline = "top",
          this.d_B.beginPath(),
          this.d_B.strokeStyle = "#666666",
          this.d_B.lineWidth = 2,
          this.d_B.strokeText(a, 0, 0),
          this.d_B.closePath())
    }
    ;
    this.Line = function(a) {
      a = a.split("|");
      let x
      if (0 != a.length)
        for (this.d_B.strokeStyle = "#666666",
                 this.d_B.lineWidth = 2,
                 x = 0; x < a.length; x++) {
          var b = a[x].split(",");
          4 == b.length && (this.d_B.beginPath(),
              this.d_B.moveTo(parseInt(this.g(b[0]), 16), parseInt(this.g(b[1]), 16)),
              this.d_B.lineTo(parseInt(this.g(b[2]), 16), parseInt(this.g(b[3]), 16)),
              this.d_B.stroke(),
              this.d_B.closePath())
        }
    }
    ;
    this.d_Ac = function(a) {
      if ("0" == a)
        a = unescape("%u952E%u540D%u5B57");
      else if ("1" == a)
        a = unescape("%u6210%u5B57%u5B57%u6839");
      else if ("2" == a)
        a = unescape("%u4E00%u7EA7%u7B80%u7801");
      else if ("3" == a)
        a = unescape("%u4E94%u7B14%u89C4%u5B9A");
      else {
        var b = "";
        -1 != "gfd".indexOf(a) && (b = unescape("%u6A2A"));
        -1 != "gfd".indexOf(a) && (b = unescape("%u7AD6"));
        -1 != "tre".indexOf(a) && (b = unescape("%u6487"));
        -1 != "yui".indexOf(a) && (b = unescape("%u637A"));
        -1 != "nbv".indexOf(a) && (b = unescape("%u6298"));
        var c = "";
        -1 != "ghtyn".indexOf(a) && (c = unescape("%u5DE6%u53F3"));
        -1 != "fjrub".indexOf(a) && (c = unescape("%u4E0A%u4E0B"));
        -1 != "dkeiv".indexOf(a) && (c = unescape("%u6742%u5408"));
        a = unescape("%u8BC6%u522B%u7801%3A%u672B%u7B14") + b + unescape("%u533A-") + c + unescape("%u7ED3%u6784")
      }
      this.d_B.font = "normal 12px " + unescape("%u5B8B%u4F53");
      this.d_B.textBaseline = "top";
      this.d_B.fillStyle = "#666666";
      this.d_B.fillText(a, 54, 196);
      this.d_B.measureText(a)
    }
    ;
    this.d_Au = function(a, b) {
      var c;
      if ("0" == a)
        c = unescape("%u952E%u540D%u5B57");
      else if ("1" == a)
        c = unescape("%u6210%u5B57%u5B57%u6839");
      else if ("2" == a)
        c = unescape("%u4E00%u7EA7%u7B80%u7801");
      else if ("3" == a)
        c = unescape("%u4E94%u7B14%u89C4%u5B9A");
      else {
        var d = "#666666";
        1 == b ? d = this.FillColor1 : 2 == b ? d = this.FillColor2 : 3 == b ? d = this.FillColor3 : 4 == b && (d = this.FillColor4);
        c = "";
        -1 != "gfd".indexOf(a) && (c = unescape("%u6A2A"));
        -1 != "gfd".indexOf(a) && (c = unescape("%u7AD6"));
        -1 != "tre".indexOf(a) && (c = unescape("%u6487"));
        -1 != "yui".indexOf(a) && (c = unescape("%u637A"));
        -1 != "nbv".indexOf(a) && (c = unescape("%u6298"));
        var g = "";
        -1 != "ghtyn".indexOf(a) && (g = unescape("%u5DE6%u53F3"));
        -1 != "fjrub".indexOf(a) && (g = unescape("%u4E0A%u4E0B"));
        -1 != "dkeiv".indexOf(a) && (g = unescape("%u6742%u5408"));
        c = unescape("%u8BC6%u522B%u7801%3A%u672B%u7B14") + c + unescape("%u533A-") + g + unescape("%u7ED3%u6784");
        this.d_B.font = "200px " + unescape("%u5B8B%u4F53");
        this.d_B.textBaseline = "top";
        a = a.toUpperCase();
        this.d_B.fillStyle = d;
        this.d_B.fillText(a, 50, 10);
        this.d_B.measureText(a)
      }
      this.d_B.font = "normal 14px " + unescape("%u5B8B%u4F53");
      this.d_B.textBaseline = "top";
      this.d_B.fillStyle = "#666666";
      this.d_B.fillText(c, 5, 3);
      this.d_B.measureText(c)
    }
    ;
    this.FillColorZG = "#98a26f";
    this.ZG_Show = function(a) {
      this.init();
      a = a.split(":");
      4 == a.length && (this.d_AE(a[1]),
          this.Line(a[2]),
          this.ZG_FillColor(a[3]))
    }
    ;
    this.ZG_FillColor = function(a) {
      this.type = "ZG"
      a = a.split("|");
      let c
      let x
      for (x = 0; x < a.length; x++) {
        var b = a[x].split(",")
              c = this.d_B.getImageData(0, 0, this.d_h.width, this.d_h.height)
            , c = this.m(c, this.FillColorZG, parseInt(this.g(b[0]), 16), parseInt(this.g(b[1]), 16));
        this.d_B.putImageData(c, 0, 0)
      }

    }
    ;
    this.d_BB = function(a) {
      this.d_B.font = "14px " + unescape("%u5B8B%u4F53");
      this.d_B.textBaseline = "top";
      this.d_B.fillStyle = "#666";
      this.d_B.fillText(a, 0, 196);
      this.d_B.measureText(a)
    }
    ;
    this.d_Aw = function(a) {
      a = a.toLowerCase();
      this.d_B.font = "14px " + unescape("%u5B8B%u4F53");
      this.d_B.textBaseline = "top";
      this.d_B.fillStyle = "#666666";
      this.d_B.fillText(a, 2, 196);
      this.d_B.measureText(a)
    }
    ;
    this.d_AO = function(a, b) {
      var c = a.split("|")
          , d = "";
      let x
      for (x = 0; x < c.length; x++) {
        var h = c[x].split(",");
        if (parseInt(h[0]) == b) {
          var e = this.d_B.getImageData(0, 0, this.d_h.width, this.d_h.height);
          // for (let i=3; i<=e.data.length;i+=4){
          //   if(e.data[i-1]==0&&e.data[i-2]==0&&e.data[i-3]==0){
          //     e.data[i] = 26
          //   }
          // }
          1 == b ? d = this.FillColor1 : 2 == b ? d = this.FillColor2 : 3 == b ? d = this.FillColor3 : 4 == b && (d = this.FillColor4);
          e = this.m(e, d, parseInt(this.g(h[1]), 16), parseInt(this.g(h[2]), 16));
          // debugger
          // for (let i in e.data){
          //   if (e.data[i]==26){
          //     e.data[i] = 255
          //   }
          // }
          for (let i=3; i<=e.data.length;i+=4){
            if(e.data[i-1]!=0&&e.data[i-2]!=0&&e.data[i-3]!=0){
              e.data[i] = 255
            }
            if(e.data[i-1]==128&&e.data[i-2]==128&&e.data[i-3]==128){
              e.data[i] = 0
              e.data[i-1] = 0
              e.data[i-2] = 0
              e.data[i-3] = 0
            }
          }
          this.d_B.putImageData(e, 0, 0)
        }
      }
    }
    ;
    this.d_BA = function(a) {
      var b = this.d_B.getImageData(0, 0, this.d_h.width, this.d_h.height);
      a = a.split("|");
      var c = "";
      for (x = 0; x < a.length; x++) {
        var d = a[x].split(",");
        1 == parseInt(d[0]) ? c = this.FillColor1 : 2 == parseInt(d[0]) ? c = this.FillColor2 : 3 == parseInt(d[0]) ? c = this.FillColor3 : 4 == parseInt(d[0]) && (c = this.FillColor4);
        b = m(b, c, parseInt(g(d[1]), 16), parseInt(g(d[2]), 16))
      }
      this.d_B.putImageData(b, 0, 0)
    }
    ;
    this.d_AS = function() {
      var a;
      a = "" + String.fromCharCode(119);
      a = a + "w" + String.fromCharCode(119);
      a = a + "." + String.fromCharCode(100);
      a += String.fromCharCode(97);
      a += String.fromCharCode(122);
      a += String.fromCharCode(105);
      a += String.fromCharCode(98);
      a += String.fromCharCode(97);
      a = a + ".c" + String.fromCharCode(110);
      this.d_B.font = "14px " + unescape("%u5B8B%u4F53");
      this.d_B.textBaseline = "top";
      this.d_B.fillStyle = "#b8b8b8";
      this.d_B.fillText(a, 80, 196);
      this.d_B.measureText(a)
    }
    ;
  }
  m(a, b, c, d) {
    // debugger
    var h, e;
    b = b.toUpperCase();
    "#" == b.charAt(0) && (b = b.substring(1, b.length));
    e = Array(3);
    e.r = b.substring(0, 2);
    e.g = b.substring(2, 4);
    e.b = b.substring(4, 6);
    e.r = parseInt(e.r, 16);
    e.g = parseInt(e.g, 16);
    e.b = parseInt(e.b, 16);
    isNaN(e.r) && (e.r = 0);
    isNaN(e.g) && (e.g = 0);
    isNaN(e.b) && (e.b = 0);
    b = e.r;
    h = e.g;
    e = e.b;
    d = this.l(a, c, d);
    c = d[3];
    if (this.n(c, this.p(b, h, e), 120))
      return a;
    d = [d];
    for (var f, g = a.width - 1, k = a.height - 1; f = d.pop(); )
      this.n(f[3], c, 120) && (a.data[f[0]] = b,
          a.data[f[0] + 1] = h,
          a.data[f[0] + 2] = e,
      0 < f[1] && d.push(this.l(a, f[1] - 1, f[2])),
      f[1] < g && d.push(this.l(a, f[1] + 1, f[2])),
      0 < f[2] && d.push(this.l(a, f[1], f[2] - 1)),
      f[2] < k && d.push(this.l(a, f[1], f[2] + 1))
      );
    return a
  }
  l(a, b, c) {
    var d = 4 * (c * a.width + b);
    a = this.p(a.data[d], a.data[d + 1], a.data[d + 2]);
    // a = this.p(255, 255, 255);
    return [d, b, c, a]
  }
  p(a, b, c) {
    // return a = 0 | (a & 255) << 16 | (b & 255) << 8 | c & 255
    if(a==0&&b==0&c==0&&this.type=="CZ"){
      return a = 16777215
    }
    return a = 0 | (a & 255) << 16 | (b & 255) << 8 | c & 255
  }
  n(a, b, c) {
    return  0 === c ? a === b : Math.abs((a >> 16 & 255) - (b >> 16 & 255)) <= c && Math.abs((a >> 8 & 255) - (b >> 8 & 255)) <= c && Math.abs((a & 255) - (b & 255)) <= c
  }
  g(a) {
    a = a.replace("a", "2E");
    a = a.replace("b", "63");
    a = a.replace("c", "37");
    a = a.replace("d", "2D");
    a = a.replace("f", "80");
    a = a.replace("e", "30");
    a = a.replace("h", "64");
    a = a.replace("g", "35");
    a = a.replace("i", "2C");
    a = a.replace("j", "2F");
    a = a.replace("k", "6A");
    a = a.replace("l", "66");
    a = a.replace("m", "7E");
    a = a.replace("n", "62");
    a = a.replace("o", "82");
    a = a.replace("p", "81");
    a = a.replace("r", "65");
    a = a.replace("q", "5F");
    a = a.replace("s", "5E");
    a = a.replace("t", "6B");
    a = a.replace("u", "7B");
    a = a.replace("w", "7F");
    a = a.replace("v", "39");
    a = a.replace("y", "69");
    a = a.replace("x", "84");
    a = a.replace("z", "31");
    a = a.replace("G", "79");
    a = a.replace("H", "33");
    a = a.replace("I", "60");
    a = a.replace("J", "3B");
    a = a.replace("K", "78");
    a = a.replace("L", "27");
    a = a.replace("M", "2B");
    a = a.replace("N", "38");
    a = a.replace("O", "74");
    a = a.replace("Q", "5D");
    a = a.replace("P", "3A");
    a = a.replace("T", "7C");
    a = a.replace("S", "7A");
    a = a.replace("R", "67");
    a = a.replace("X", "7D");
    a = a.replace("W", "34");
    a = a.replace("V", "2A");
    a = a.replace("U", "29");
    a = a.replace("Y", "3C");
    return a = a.replace("Z", "32")
  }
}
var kuw_load_WB_ColorSHow = "ok";
