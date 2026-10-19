# 项目演示图片

本目录用于本地演示图片。暂时不要生成或提交假照片；准备真实、自然的本地图片后，将文件放在本目录，并保持以下文件名：

- books.jpg：儿童绘本一套，整齐摆放在桌面上
- coffee-set.jpg：手冲咖啡壶、滤杯和量匙
- air-fryer.jpg：九成新空气炸锅，展示干净内胆
- bedside-table.jpg：白色床头柜，展示抽屉和整体成色
- drying-rack.jpg：折叠晾衣架，展示展开状态
- warm-lamp.jpg：暖光可调台灯，展示点亮后的柔和光线

数据库中的 item.image_url 使用 /demo/文件名.jpg。Vite 会从 frontend/public 根目录提供这些静态资源；没有图片时，现有前端会显示无图占位。
