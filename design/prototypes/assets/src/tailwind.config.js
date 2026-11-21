/** 原型 Tailwind 配置：构建命令见 README.md */
module.exports = {
  content: ['./design/prototypes/*.html', './design/prototypes/assets/js/*.js'],
  theme: {
    extend: {
      colors: {
        brand: { 50:'#EEF5FF',100:'#D9E8FF',200:'#B5D2FF',300:'#84B4FB',400:'#4F8FF2',500:'#2A78D6',600:'#1E63C2',700:'#184F9E',800:'#163F7C',900:'#132F5A' },
        gas:   { cng:'#0E9F7E', cngbg:'#E3F6F0', lng:'#5B4BD6', lngbg:'#ECEAFD' },
        ink:   { 900:'#17202E', 700:'#475467', 500:'#8A94A6', 300:'#C9D0DB', 100:'#EEF1F5' },
        page:  '#F4F6F9',
        money: '#E8501F',
        ok:'#14A06F', warn:'#E49B0F', bad:'#D93B3B'
      },
      fontFamily: { sans: ['-apple-system','BlinkMacSystemFont','"PingFang SC"','"Microsoft YaHei"','"Segoe UI"','sans-serif'] },
      boxShadow: { card:'0 1px 2px rgba(16,24,40,.04), 0 2px 10px rgba(16,24,40,.05)', pop:'0 12px 32px rgba(16,24,40,.16)' },
      borderRadius: { xl2:'14px' }
    }
  }
};
