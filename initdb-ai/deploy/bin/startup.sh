#!/bin/sh

error_exit ()
{
    echo "ERROR: $1 !!"
    exit 1
}
[ ! -e "$JAVA_HOME/bin/java" ] && JAVA_HOME=$HOME/jdk/java
[ ! -e "$JAVA_HOME/bin/java" ] && JAVA_HOME=/usr/java
[ ! -e "$JAVA_HOME/bin/java" ] && unset JAVA_HOME


if [ -z "$JAVA_HOME" ]; then
  if $darwin; then

    if [ -x '/usr/libexec/java_home' ] ; then
      export JAVA_HOME=`/usr/libexec/java_home`

    elif [ -d "/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home" ]; then
      export JAVA_HOME="/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Home"
    fi
  else
    JAVA_PATH=`dirname $(readlink -f $(which javac))`
    if [ "x$JAVA_PATH" != "x" ]; then
      export JAVA_HOME=`dirname $JAVA_PATH 2>/dev/null`
    fi
  fi
  if [ -z "$JAVA_HOME" ]; then
        error_exit "Please set the JAVA_HOME variable in your environment, We need java(x64)! jdk11 or later is better!"
  fi
fi


export SERVER="initDB-AI"


export JAVA_HOME
export JAVA="$JAVA_HOME/bin/java"
export BASE_DIR=`cd $(dirname $0)/..; pwd`

export CUSTOM_SEARCH_LOCATIONS=file:${BASE_DIR}/conf/
export LANG="zh_CN.UTF-8"

#===========================================================================================
# JVM Configuration
#===========================================================================================
JAVA_OPT="${JAVA_OPT} -server -Xms1g -Xmx1g -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=256m -Dfile.encoding=UTF-8 -Duser.language=zh -Duser.region=CN"
JAVA_OPT="${JAVA_OPT} -XX:-OmitStackTraceInFastThrow -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=${BASE_DIR}/logs/java_heapdump.hprof"
JAVA_OPT="${JAVA_OPT} -XX:-UseLargePages"
JAVA_OPT="${JAVA_OPT} ${INIT_CONFIG}"


JAVA_OPT="${JAVA_OPT} -Dsystem.basedir=${BASE_DIR}"
JAVA_OPT="${JAVA_OPT} -Dloader.path=${BASE_DIR}/plugins/health -jar ${BASE_DIR}/target/${SERVER}.jar"
JAVA_OPT="${JAVA_OPT} --spring.config.location=${CUSTOM_SEARCH_LOCATIONS}"
JAVA_OPT="${JAVA_OPT} --logging.config=${BASE_DIR}/conf/logback.xml"
JAVA_OPT="${JAVA_OPT} --server.max-http-header-size=524288"
JAVA_OPT="${JAVA_OPT} --jasypt.encryptor.password=${JASYPT_PASSWORD}"

if [ ! -d "${BASE_DIR}/logs" ]; then
  mkdir ${BASE_DIR}/logs
fi

echo "$JAVA ${JAVA_OPT}"


echo "initDB-AI is starting ...."


# check the start.out log output file
#if [ ! -f "${BASE_DIR}/logs/default.log" ]; then
#  touch "${BASE_DIR}/logs/default.log"
#fi
# start
#echo "$JAVA ${JAVA_OPT}" > ${BASE_DIR}/logs/start.out 2>&1 &
#nohup $JAVA ${JAVA_OPT} sungl >> ${BASE_DIR}/logs/start.out 2>&1 &
nohup $JAVA ${JAVA_OPT} initDB-AI >> /dev/null 2>&1 &
echo "initDB-AI is starting，you can check the ${BASE_DIR}/logs/default.log"
