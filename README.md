# GDSN Transformation Template

## Prerequisites to Build

1. Install the Java Platform 1.8 (JDK)
2. Ensure the `JAVA_HOME` environment variable points to your JDK installation
  * Start a bash shell; on Ubuntu, this is the default shell used by the Terminal application
  * Run the command: `env | grep JAVA`
  * You should see a line starting with: `JAVA_HOME`
  * If not, add it to your .bashrc file as follows:
    * to get the base path of the JDK, run the command: `readlink -ze /usr/bin/javac | xargs -0 dirname -z | xargs -0 dirname`
    * edit the .bashrc file: `vi ~/.bashrc`
    * move to the end of the file with the arrow keys and press the "a" key (which puts vi into append mode); start a new line after the comment `# User specific aliases and functions`
    * enter the text: `export JAVA_HOME=`(append with the appropriate path to your java installation)
    * add another line: `export PATH=$PATH:$JAVA_HOME/bin`
    * press the `Esc` key, followed by `:wq` (which writes your changes and quits vi)
    * reload the .bashrc file: `source ~/.bashrc`
3. Install Apache Maven: https://maven.apache.org/install.html
4. Add the Maven `bin` directory to your `PATH` environment variable. See the above example of updating the path by editing the .bashrc file. For example, your .bashrc file might now contain:
  * `export PATH=$PATH:$JAVA_HOME/bin:/opt/apache-maven-[VERSION]/bin`

## Building the Project

Note: All commands must be run on the solution maven project.

1. Refer connectors-solution/settings.xml
    * `Generate Personal Access Token using ` https://riversand.atlassian.net/wiki/spaces/RP/pages/1226605554/GDSN+Customer+Model+Transform+Design#Maven
    * `Copy the conectors-solution/settings.xml to ~/.m2`
    * `Update ~/.m2/settings.xml with your access token (generated in step 1) and git user name`
2. Change directory to the solution project:
    * `cd ~/git/gdsn-translation-app-template/connectors-solution`
3. Clean up all directories:
    * `mvn clean`
4. Compile the projects:
    * `mvn compile`
5. Package the jars according to each individual settings in each module level project:
    * `mvn package`
6. To get the package without running tests:
    * `mvn package -DskipTests`

## Documentation

1. https://riversand.atlassian.net/wiki/spaces/RP/pages/1226605554/GDSN+Customer+Model+Transform+Design
