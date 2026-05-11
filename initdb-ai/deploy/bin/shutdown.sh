#!/bin/bash

checkExe(){
  if [ $1 -eq 0 ]; then
      echo "execute completed..."
  else
      echo "execute failed..."
      exit -1
  fi
}

cd `dirname $0`/../target
target_dir=`pwd`
APP_NAME="initDB-AI"

APP_ID=`ps ax | grep -i ${APP_NAME} | grep ${target_dir} | grep java | grep -v grep | awk '{print $1}'`
echo "APP_NAME: ${APP_NAME}"
echo "APP_ID: ${APP_ID}"
if [ -n "$APP_ID" ]; then
  echo "-----------> ${APP_ID} killed ..."
  kill ${APP_ID}
  checkExe $?
else
  checkExe 0
  echo "-----------> The service was not started ..."
fi