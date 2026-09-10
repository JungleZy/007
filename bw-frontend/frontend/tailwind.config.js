const defaultTheme = require('tailwindcss/defaultTheme')

/** @type {import("@types/tailwindcss/tailwind-config").TailwindConfig } */
module.exports = {
  mode: 'jit',
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    spacing: {
      '1': '8px',
      '2': '12px',
      '3': '16px',
      '4': '24px',
      '5': '32px',
      '6': '48px',
    },
    borderRadius: {
      DEFAULT: '2px',
      'none': '0',
      '1': '5px',
      '2': '10px'
    },
    extend: {
      fontFamily: {
        sans: ['"Inter var"', ...defaultTheme.fontFamily.sans],
      },
      animation: {
        wiggle: 'wiggle 1s linear forwards',
      },
      keyframes: {
        wiggle: {
          '0%': {transform: 'rotate(0deg)'},
          '50%': {transform: 'rotate(180deg)'},
          '100%': {transform: 'rotate(360deg)'},
        }
      }
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
    require('@tailwindcss/aspect-ratio'),
  ],
}