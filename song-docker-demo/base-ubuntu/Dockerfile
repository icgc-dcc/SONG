FROM openjdk:8-jre-stretch
MAINTAINER OVERTURE <contact@overture.bio>

ENV DCC_HOME /opt/dcc
ENV DCC_DATA $DCC_HOME/data
ENV DCC_CONFIG $DCC_HOME/config

RUN apt update &&  \
    apt install -y wget git sqlite3 python3 python3-pip build-essential libssl-dev python3-dev nmap vim curl telnet postgresql software-properties-common && \ 
    useradd -m dcc && \
    echo "alias ls='ls --color'" >> ~/.bashrc && \
    echo "alias ll='ls -l'" >> ~/.bashrc && \
    echo "alias lr='ll -rt'" >> ~/.bashrc && \
    echo "alias la='ll -a'" >> ~/.bashrc && \
    mkdir -p $DCC_HOME


