import {ref, watch} from 'vue'
import {AES, enc, mode, pad} from 'crypto-js'
import {message} from "ant-design-vue";
import {useClipboard} from '@vueuse/core'

const testCode = 'wjkj2025~'
const aseKey = 'wisdom23'
const parse1 = enc.Utf8.parse(aseKey)
const parse2 = {
	mode: mode.ECB,
	padding: pad.Pkcs7
}
export default function VerifyLicense() {
	const pc = ["o", "l", "L", "i", "I"]
	const uploadInput = ref(null)
	const licenseCode = ref('')
	const isPass = ref(true)
	const tips = ref({
		title: '未查询到相关授权信息或设备已重置，请进行授权',
		code: '',
		codeTips: '设备码获取中...'
	})
	const {text, copy, copied, isSupported} = useClipboard({source: tips.value.code})
	watch(copied, () => {
		if (copied.value) {
			message.success("设备码复制成功!")
		}
	})
	const triggerFileUpload = () => {
		uploadInput.value.click()
	}
	const handleFileUpload = (event) => {
		console.log(event.target.files[0])
		parseFileContent(event.target.files[0])
	}

	async function parseFileContent(file) {
		if (!file) return;
		const reader = new FileReader();
		reader.onload = function (e) {
			const content = e.target.result;
			licenseCode.value = content.split('\n')
		};
		reader.readAsText(file);
	}

	const generateUUID = () => {
		return `xxxx-xxxx-xxxx-xxxx-xxxx-xxxx-xxxx-xxxx`.replace(/[xy]/g, (c) => {
			return judgeString(c)
		});
	}
	const judgeString = (c) => {
		const r = Math.random() * 16 | 0
		const v = c === 'x' ? r : (r & 0x3 | 0x8)
		const s = v.toString(16)
		if (pc.includes(s)) {
			return judgeString()
		} else {
			return s
		}
	}
	const handleCode = (e) => {
		isPass.value = false
		if (e) {
			tips.value.code = e.machineCode
			tips.value.codeTips = '设备码获取成功'
		} else {
			localforage.removeItem("1").then(() => {
				localforage.removeItem("2").then(() => {
					const g = generateUUID()
					console.log(g)
					tips.value.code = g
					localforage.removeItem("2").then(() => {
						localforage.setItem("2", {
							machineCode: g
						}).then(() => {
							tips.value.codeTips = '设备码获取成功'
						}).catch(e => {
							message.error("授权失败!")
						})
					})
				})
			})
		}
	}
	const resetCode = () => {
		handleCode()
	}
	const generateLicense = () => {
		const nowTime = new Date().getTime()
		const ls = tips.value.code + ':' + nowTime
		licenseCode.value = AES.encrypt(ls, parse1, parse2).toString()
	}
	const submitLicense = () => {
		licenseCode.value = document.getElementById('licenseCodeDiv').innerText.replace(/\s+/g, '').replace(/<[^>]+>/g, '')
		console.log(licenseCode.value);
		if (!licenseCode.value) {
			message.error("授权失败,请输入授权码!")
			return
		}
		if (licenseCode.value !== testCode) {
			try {
				let encrypt = AES.decrypt(
					licenseCode.value,
					parse1,
					parse2
				).toString(enc.Utf8)
				const info = encrypt.split(':')
				if (info.length < 2 || info.length > 3) {
					message.error("授权失败,授权码错误!")
					return
				}
				if (info[0] !== tips.value.code) {
					message.error("授权失败,授权码错误!")
					return
				}
			} catch (e) {
				message.error("授权失败,授权码错误!")
				return
			}
		}
		localforage.clear().then(() => {
			localforage.setItem("2", {
				machineCode: tips.value.code
			}).then(() => {
				localforage.setItem("1", {
					license: licenseCode.value,
					licenseTime: 0,
					duration: 0
				}).then(() => {
					message.success("授权成功,3秒后自动刷新授权信息!")
					setTimeout(() => {
						location.reload()
					}, 3000)
				}).catch(e => {
					message.error("授权失败!")
				})
			}).catch(e => {
				message.error("授权失败!")
			})
		}).catch(e => {
			console.log(e)
		})
	}
	const onIntervalLicense = (license, deadline) => {
		setTimeout(() => {
			if (license.duration <= deadline) {
				license.duration += 10
				localforage.setItem("1", license).then((res) => {
					onIntervalLicense(res, deadline)
				})
			} else {
				isPass.value = false
			}
		}, 10000)
	}
	localforage.getItem("2").then(machineCode => {
			if (!machineCode) {
				handleCode(null)
			} else {
				localforage.getItem("1").then(license => {
						if (!license) {
							handleCode(machineCode)
						} else {
							if (license.license !== testCode) {
								let encrypt;
								try {
									encrypt = AES.decrypt(
										license.license,
										parse1,
										parse2
									).toString(enc.Utf8)
								} catch (e) {
									handleCode(machineCode)
								}
								if (encrypt) {
									const info = encrypt.split(':')
									if ((info.length === 2 || info.length === 3) && info[0] === machineCode.machineCode) {
										const diff = new Date().getTime() - info[1]
										const deadline = (info.length === 2 ? 30 : info[2]) * 24 * 60 * 60
										// if (diff > 0 && diff < 86400000 * (info.length === 2 ? 30 : info[2]) && license.licenseTime <= diff) {
										if (license.duration <= deadline) {
											license.licenseTime = diff
											localforage.setItem("1", license).then(() => {
												isPass.value = true
												if (!license.duration) {
													license.duration = 0
												}
												onIntervalLicense(license, deadline)
											}).catch(e => {
												handleCode(machineCode)
											})
										} else {
											handleCode(machineCode)
										}
									} else {
										handleCode(machineCode)
									}
								} else {
									handleCode(machineCode)
								}
							} else {
								isPass.value = true
							}
						}
					}
				)
			}
		}
	)
	return {
		licenseCode,
		isPass,
		tips,
		uploadInput,
		copy,
		resetCode,
		submitLicense,
		generateLicense,
		triggerFileUpload,
		handleFileUpload
	}
}
