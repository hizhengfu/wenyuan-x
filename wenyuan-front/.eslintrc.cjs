module.exports = {
  env: {
    browser: true,
    node: true,
    es6: true,
    jest: true,
    commonjs: true,
  },

  extends: ['eslint:recommended', 'plugin:vue/vue3-recommended', 'prettier'],
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 2020,
    sourceType: 'module',
    ecmaFeatures: {
      experimentalObjectRestSpread: true,
      jsx: true,
    },
  },
  plugins: ['vue'],
  rules: {
    indent: ['off', 2],
    quotes: ['error', 'single'],
    'block-spacing': 'error',
    'no-unused-vars': 'warn',
    // 'object-curly-spacing': 'error',
    'no-console': 'warn',
    'no-undef': 'off',
    'vue/multi-word-component-names': [
      'warn',
      {
        ignores: ['index'],
      },
    ],
    'vue/valid-v-model': 'warn',
    // "vue/return-in-computed-property": ["error", {
    //   "eslint-disable-next-line": false
    // }],
    'vue/no-template-key': 'warn',
    'vue/valid-v-for': 'warn',
    'vue/require-v-for-key': 'warn',
  },
};
