This is a simple UI to manage notes on your desktop.
While there are many Notes taking Apps out there, I didn't find anything simple enough on the desktop.

*Features:*
 
- Simple UI. Fire up the App and start writing plain text notes
 - Cross Platform. Written with JavaFX and runs on Windows,Mac and Linux
 - Attach arbitrary files to a Note
 - Include notes to each attachment 
 - Note Text, Attachment file names and attachment notes are all searchable
 - You do not need Java installed on your desktop to run it (See Below)

*Internals:*

It's written in Kotlin. Uses Java FX, JDK11 and Lucene internally.  In order to build it, you'd need JDK 11 installed.
Build Steps:
  - Install JDK 11 in a directory (eg c:\jdk11)
  - set JAVA_HOME=c:\jdk11
  - set PATH=%JAVA_HOME%/bin;%PATH%
  - gradlew runtime
  - the runnable image is built in build\image

Running the Image:
  - Once built, simply execute run-cmd.bat from Windows command line OR goto download [prebuilt zip file](https://github.com/praveenray/my-notes/releases/tag/1.0.0) and follow instruction to download and run the prebuilt zip file.
