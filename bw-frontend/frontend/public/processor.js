class SineProcessor extends AudioWorkletProcessor {
  constructor(options) {
    super(options);
    // 音频参数
    this.currentVolume = 0;
    this.targetGain = 0;
    this.phase = 0;
    this.phaseIncrement = 0;
    this.frequency = 1000;
    this.gain = 1;
    this.sampleRate = 48000;
    this.alpha = 0;
    this.model = true;
    this.pressed = false;

    // 莫尔斯码控制
    this.morseCode = null;
    this.currentSymbolIndex = 0;
    this.currentSubSymbolIndex = 0;
    this.currentRemainingSamples = 0;
    this.currentSymbolType = -1; // 0:点 1:划 2+:间隔
    this.datumSamples = {};
    this.startTime = null

    // 时间参数
    this.criterion = 83;
    this.ratio = {
      dot: 1, dash: 3, gap: 1,
      word: 3, suite: 5, leaf: 7
    };
    this.handleDatum();
    this.progressFlag = {
      shouldPost: false,
      isSymbolEnd: false,
      currentIndex: 0,
      currentSubIndex: 0
    };
    this.updatePhaseIncrement();
    this.port.onmessage = (event) => this.handleMessage(event.data);
  }

  handleMessage(data) {
    switch (data.type) {
      case 'start':
        this.isPause = false
        this.resetPlayback();
        this.morseCode = data.morseCode;
        this.port.postMessage({ type: 'playing', status: 'start' });
        break;
      case 'addCode':
        // for (let i=0; i<this.progressFlag.currentIndex; i++) {
        //   // delete this.morseCode[i] = null
        //   delete this.morseCode[i]
        // }
        this.morseCode.push(...data.morseCode);
        console.log(this.morseCode)
        break;
      case 'stop':
        this.resetPlayback();
        this.port.postMessage({ type: 'playing', status: 'finish' });
        break;
      case 'pause':
        this.isPause = data.data;
        this.port.postMessage({
          type: 'pause',
          status: this.isPause ? 'pause' : 'playing'
        });
        break;
      case 'criterion':
        this.criterion = data.data;
        this.handleDatum();
        break;
      case 'ratio':
        this.ratio = data.data;
        this.handleDatum();
        break;
      case 'volume':
        this.gain = data.data;
        break;
      case 'frequency':
        this.frequency = data.data;
        this.updatePhaseIncrement();
        break;
      case 'sampleRate':
        this.sampleRate = data.data;
        this.updatePhaseIncrement();
        this.handleDatum();
        break;
      case 'pressed':
        this.pressed = data.data;
        break;
      case 'model':
        this.model = data.data;
        break;
      default:
        console.warn('Unknown message type:', data.type);
    }
  }

  process(inputs, outputs) {
    const output = outputs[0];
    if (!output) return true;
    const channel = output[0];
    if (!channel) return true;

    // 缓存局部变量
    const model = this.model;
    const morseCode = this.morseCode;
    const isPause = this.isPause;
    const pressed = this.pressed;
    const gain = this.gain;
    const alpha = this.alpha;
    const phaseIncrement = this.phaseIncrement;

    // 模式处理
    if (model) {
      if (!morseCode || isPause) {
        this.targetGain = 0;
      } else {
        this.handleAutomatedGain(channel.length);
      }
    } else {
      this.targetGain = pressed ? gain : 0;
    }

    // 信号生成
    let phase = this.phase;
    let currentVolume = this.currentVolume;
    const targetGain = this.targetGain;

    for (let i = 0; i < channel.length; i++) {
      // 平滑增益
      if (currentVolume !== targetGain) {
        currentVolume += (targetGain - currentVolume) * alpha;
      }

      // 相位处理
      channel[i] = Math.sin(phase) * currentVolume;
      phase += phaseIncrement;
      if (phase >= 2 * Math.PI) phase -= 2 * Math.PI;
    }

    // 保存状态
    this.phase = phase;
    this.currentVolume = currentVolume;

    // 进度通知
    if (this.progressFlag.shouldPost) {
      const symbol = this.morseCode[this.progressFlag.currentIndex];
      this.port.postMessage({
        type: 'playing',
        status: 'progress',
        i: this.progressFlag.currentIndex,
        j: Math.floor(this.progressFlag.currentSubIndex / 2),
        codeLength:this.morseCode.length,
        value: symbol?.value,
        key: symbol?.key
      });
      this.progressFlag.shouldPost = false;
      for (let i=0; i<this.progressFlag.currentIndex; i++) {
        // delete this.morseCode[i] = null
        delete this.morseCode[i]
      }
      // console.log(this.morseCode)
    }

    return true;
  }

  handleAutomatedGain() {

    if(this.startTime!==null&&this.currentRemainingSamples>0&&this.startTime+this.currentRemainingSamples<Date.now()){
      this.currentRemainingSamples = -1
      this.startTime = null
    }
    if (this.currentRemainingSamples > 0) {
      // this.currentRemainingSamples--;
      this.targetGain = (this.currentSymbolType < 2) ? this.gain : 0;
      return;
    }

    const symbol = this.morseCode[this.currentSymbolIndex];
    if (!symbol) {
      this.targetGain = 0;
      return this.resetPlayback();
    }

    const subSymbol = symbol.value[this.currentSubSymbolIndex];
    const samples = this.datumSamples[subSymbol] || 0;
    if (samples > 0) {
      this.currentRemainingSamples = samples - 1;
      this.startTime = Date.now()
      this.currentSymbolType = subSymbol;
      this.targetGain = (subSymbol < 2) ? this.gain : 0;
      this.advanceIndex(subSymbol < 2);
    } else {
      this.advanceIndex(false);
    }
  }

  advanceIndex(isSymbolEnd) {
    // 设置进度标志
    if (isSymbolEnd) {
      this.progressFlag = {
        shouldPost: true,
        isSymbolEnd: true,
        currentIndex: this.currentSymbolIndex,
        currentSubIndex: this.currentSubSymbolIndex
      };
    }

    // 更新索引
    this.currentSubSymbolIndex++;

    // 检查是否越界
    if (this.currentSubSymbolIndex >= this.morseCode[this.currentSymbolIndex].value.length) {
      // 触发字符结束事件
      if (!isSymbolEnd) {
        this.progressFlag.shouldPost = true;
        this.progressFlag.currentIndex = this.currentSymbolIndex;
        this.progressFlag.currentSubIndex = this.currentSubSymbolIndex;
      }

      this.currentSymbolIndex++;
      this.currentSubSymbolIndex = 0;
    }

    // 结束检测
    if (this.currentSymbolIndex >= this.morseCode.length) {
      this.port.postMessage({ type: 'playing', status: 'finish' });
      this.resetPlayback();
    }
  }

  resetPlayback() {
    this.currentSymbolIndex = 0;
    this.currentSubSymbolIndex = 0;
    this.currentRemainingSamples = 0;
    this.currentSymbolType = -1;
    this.morseCode = null;
    this.progressFlag = {
      shouldPost: false,
      isSymbolEnd: false,
      currentIndex: 0,
      currentSubIndex: 0
    };
  }

  handleDatum() {
    const msToSamples = (ms) => {
      const exact = (ms / 1000) * this.sampleRate;
      return exact < 1 ? 1 : Math.round(exact);
    };

    this.datumSamples = {
      0: this.criterion * this.ratio.dot,
      1: this.criterion * this.ratio.dash,
      2: this.criterion * this.ratio.gap,
      3: this.criterion * this.ratio.word,
      4: this.criterion * this.ratio.suite,
      5: this.criterion * this.ratio.leaf
    };
  }

  updatePhaseIncrement() {
    this.phaseIncrement = (this.frequency * 2 * Math.PI) / this.sampleRate;
    this.alpha = 1 - Math.exp(-1 / (0.003 * this.sampleRate));
  }
}

registerProcessor('sine-processor', SineProcessor);
