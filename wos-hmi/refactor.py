import os

controller_path = rc:\Users\parad\Desktop\wosbot-main\wos-hmi\src\main\java\cl\camodev\wosbot\taskbuilder\view\TaskBuilderLayoutController.java
with open(controller_path, r, encoding=utf-8) as f:
    content = f.read()
content = content.replace(import javafx.scene.shape.CubicCurve;, import javafx.scene.shape.Path;\nimport javafx.scene.shape.MoveTo;\nimport javafx.scene.shape.CubicCurveTo;)
content = content.replace(CubicCurve, Path)
with open(controller_path, w, encoding=utf-8) as f:
    f.write(content)
