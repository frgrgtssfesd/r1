@echo off
chcp 65001 >nul
if not exist out mkdir out
javac -encoding UTF-8 -d out src\timetravel\*.java && java -cp out timetravel.Main
pause
