class SineProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.frequency = 1000;
    this.gain = 1;
    this.criterion = 83;
    this.ratio = {dot: 1, dash: 3, gap: 1, word: 3, suite: 5, leaf: 7};
    this.model = true;
    this.phase = 0;
    this.currentVolume = 0;
    this.alpha = 1 - Math.exp(-1 / (0.003 * sampleRate));
    this.resetPlayback();
    this.handleDatum();
    this.port.onmessage = ({data}) => this.handleMessage(data);
  }

  handleMessage(message) {
    switch (message.type) {
      case 'state': {
        const state = message.data;
        this.criterion = state.criterion;
        this.ratio = state.ratio;
        this.frequency = state.frequency;
        this.gain = state.volume;
        if (this.model !== state.model) this.resetPlayback();
        this.model = state.model;
        this.handleDatum();
        break;
      }
      case 'start':
        this.resetPlayback();
        this.morseCode = message.morseCode;
        this.port.postMessage({type: 'playing', status: 'start'});
        break;
      case 'addCode':
        if (!this.morseCode) this.morseCode = [];
        this.morseCode.push(...message.morseCode);
        break;
      case 'stop':
        this.resetPlayback();
        this.port.postMessage({type: 'stopped'});
        break;
      case 'pause':
        this.isPause = message.data;
        this.port.postMessage({type: 'pause', status: this.isPause ? 'pause' : 'playing'});
        break;
      case 'pressed':
        this.pressed = message.data;
        break;
    }
  }

  handleDatum() {
    this.datumSamples = ['dot', 'dash', 'gap', 'word', 'suite', 'leaf'].map(key => this.criterion * this.ratio[key] * sampleRate / 1000);
    this.phaseIncrement = this.frequency * 2 * Math.PI / sampleRate;
  }

  resetPlayback() {
    this.morseCode = null;
    this.currentSymbolIndex = 0;
    this.currentSubSymbolIndex = 0;
    this.currentRemainingSamples = 0;
    this.currentSymbolType = -1;
    this.sampleRemainder = 0;
    this.playedSamples = 0;
    this.isPause = false;
    this.pressed = false;
    this.currentVolume = 0;
    this.phase = 0;
  }

  nextSegment() {
    while (this.morseCode && this.currentRemainingSamples === 0) {
      const symbol = this.morseCode[this.currentSymbolIndex];
      if (!symbol) {
        const samples = this.playedSamples;
        this.resetPlayback();
        this.port.postMessage({type: 'playing', status: 'finish', samples});
        return;
      }
      if (this.currentSubSymbolIndex >= symbol.value.length) {
        this.morseCode[this.currentSymbolIndex] = null;
        this.currentSymbolIndex++;
        this.currentSubSymbolIndex = 0;
        continue;
      }
      const j = this.currentSubSymbolIndex++;
      this.currentSymbolType = symbol.value[j];
      const exact = this.datumSamples[this.currentSymbolType] + this.sampleRemainder;
      if (!Number.isFinite(exact) || exact < 0) {
        this.resetPlayback();
        this.port.postMessage({type: 'failure', message: '无效音频时长，请检查播报设置'});
        return;
      }
      this.currentRemainingSamples = Math.floor(exact);
      this.sampleRemainder = exact - this.currentRemainingSamples;
      if (this.currentSymbolType < 2 || j === symbol.value.length - 1) {
        this.port.postMessage({type: 'playing', status: 'progress', i: this.currentSymbolIndex,
          j: Math.floor(j / 2), codeLength: this.morseCode.length, value: symbol.value, key: symbol.key,
          sourceIndex: symbol.sourceIndex, samples: this.playedSamples});
      }
    }
  }

  process(inputs, outputs) {
    const output = outputs[0];
    if (!output?.[0]) return true;
    const channel = output[0];
    for (let i = 0; i < channel.length; i++) {
      if (this.model && !this.isPause) this.nextSegment();
      const active = !this.isPause && (this.model ? this.currentRemainingSamples > 0 && this.currentSymbolType < 2 : this.pressed);
      this.currentVolume += ((active ? this.gain : 0) - this.currentVolume) * this.alpha;
      // Silence is exact; attack smoothing cannot extend a segment or advance a paused cursor.
      channel[i] = active ? Math.sin(this.phase) * this.currentVolume : 0;
      if (!this.isPause) {
        this.phase = (this.phase + this.phaseIncrement) % (2 * Math.PI);
        if (this.model && this.currentRemainingSamples > 0) {
          this.currentRemainingSamples--;
          this.playedSamples++;
        }
      }
    }
    for (let c = 1; c < output.length; c++) output[c].set(channel);
    return true;
  }
}

registerProcessor('sine-processor', SineProcessor);
