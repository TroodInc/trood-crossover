FROM hseeberger/scala-sbt

ENV APPDIR=/home/app
ENV SRCDIR=/home/src

RUN mkdir -p $SRCDIR

WORKDIR $SRCDIR

ADD ./ $SRCDIR/
RUN sbt universal:packageZipTarball

RUN mkdir -p $APPDIR

WORKDIR $APPDIR

RUN tar zxvf /home/src/target/universal/crossover-0.1.0-SNAPSHOT.tgz -C /home/app
RUN mv /home/app/crossover-0.1.0-SNAPSHOT/* /home/app && rm -rf /home/app/crossover-0.1.0-SNAPSHOT


CMD /home/app/bin/crossover --http.port 8000 --conf
