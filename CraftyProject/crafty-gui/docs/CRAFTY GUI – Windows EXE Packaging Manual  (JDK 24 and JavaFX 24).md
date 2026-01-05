================================================================================
CRAFTY GUI – Windows EXE Packaging Manual  (JDK 24 / JavaFX 24)
================================================================================
Last tested: 2025-06-20 on Windows 10 / JDK 24.0.1 / JavaFX 24.0.1
This guide collects every pitfall we hit while turning the Maven-built
CRAFTY GUI into a working **CraftyGUI.exe** with `jpackage`.

────────────────────────────────────────────────────────────────────────────────
0 .  Quick prerequisites checklist
────────────────────────────────────────────────────────────────────────────────
▪ Java SDK 24.0.1 (the same JDK must provide **java / jlink / jpackage**)  

  java --version        # 24.0.1
  jlink --version       # 24.0.1
  jpackage --version    # 24.0.1
▪ Maven 3.9 + built with that JDK (verify the Java line in mvn --version).
▪ JavaFX 24 SDK unpacked somewhere, e.g.

C:\Users\<you>\Documents\JavafxSDK\javafx-sdk-24.0.1\
    ├─ lib\  (all javafx-*.jar)
    └─ bin\  (native DLLs)
────────────────────────────────────────────────────────────────────────────────
1 . Build & run directly from Maven (sanity check)
────────────────────────────────────────────────────────────────────────────────

cd CraftyProject\crafty-gui
mvn clean javafx:run         # the GUI must appear (be sure that core jar is created)
────────────────────────────────────────────────────────────────────────────────
2 . Maven package phase – create the runnable JAR and copy JavaFX runtime
────────────────────────────────────────────────────────────────────────────────
The pom.xml already contains:

maven-shade-plugin – produces the fat jar
(crafty-gui-0.0.1-SNAPSHOT.jar) and excludes all JavaFX modules.

maven-antrun-plugin – copies JavaFX lib and bin to
target/fxjars/lib and target/fxjars/bin.

Build:

mvn clean package
Afterwards target\ must contain :

crafty-gui-0.0.1-SNAPSHOT.jar     
fxjars\
   ├─ lib\  (javafx-*.jar, jdk.jsobject.jar, …)
   └─ bin\  (prism_*.dll, glass.dll, …)

────────────────────────────────────────────────────────────────────────────────
3 . Manual JAR test (always do this before packaging)
────────────────────────────────────────────────────────────────────────────────

cd target
java --module-path fxjars\lib ^
     --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.web ^
     -jar crafty-gui-0.0.1-SNAPSHOT.jar
If the GUI opens → you are ready for jpackage.

Common JAR-stage errors & fixes
Symptom	Root cause / fix
ResolutionException mentioning javafx.*	Forgot to exclude JavaFX modules in shade-plugin.
QuantumRenderer “no suitable pipeline”	Native DLLs not on PATH; use fxjars\bin.
Duplicate package “javafx.fxml”	Both shaded & original jar present; delete original-.jar*.

────────────────────────────────────────────────────────────────────────────────
4 . Create the installer with jpackage
────────────────────────────────────────────────────────────────────────────────
Run inside the target folder: (be sure you are in ..\CraftyProject\crafty-gui NOT in ..\CraftyProject\crafty-gui\target)

jpackage ^
  --type exe ^
  --input target ^
  --dest installer ^
  --name CraftyGUI ^
  --main-jar crafty-gui-0.0.1-SNAPSHOT.jar ^
  --main-class de.cesr.crafty.gui.main.FxMain ^
  --add-modules javafx.controls,javafx.fxml,javafx.swing,javafx.web,java.logging,java.management ^
  --module-path "C:\Users\byari-m\Documents\JavafxSDK\javafx-jmods-24.0.1" ^
  --java-options "--enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED" ^
  --win-console ^
  --win-shortcut ^
  --icon "C:\Users\byari-m\Desktop\TheFolder\Inkscap-Projects\icon.ico" ^
  --resource-dir src\installer\resources ^
  --verbose
  
 // Remark to be add in scr/...:
  --temp target\jpackage-temp ^
  
▸ This produces installer\CraftyGUI-1.0.exe

Why jmods not jars?
Using javafx-jmods-*.zip lets jlink embed JavaFX modules plus native
code directly into the custom runtime image.
────────────────────────────────────────────────────────────────────────────────
5 . Install & verify
────────────────────────────────────────────────────────────────────────────────
Run CraftyGUI-1.0.exe → installs to
C:\Program Files\CraftyGUI\

Expected layout:

CraftyGUI\
 ├─ CraftyGUI.exe              (native launcher)
 ├─ runtime\bin\jvm.dll        (custom JDK image, no java.exe – normal)
 └─ app\
     ├─ crafty-gui-0.0.1-SNAPSHOT.jar
     ├─ fxjars\lib\*.jar
     ├─ fxjars\bin\*.dll
     └─ CraftyGUI.cfg          (see below)
Double-click CraftyGUI.exe → GUI appears.

────────────────────────────────────────────────────────────────────────────────
6 . The critical CraftyGUI.cfg
────────────────────────────────────────────────────────────────────────────────
C:\Program Files\CraftyGUI\app\CraftyGUI.cfg should be:

[Application]
app.classpath=$APPDIR\crafty-gui-0.0.1-SNAPSHOT.jar
app.mainclass=de.cesr.crafty.gui.main.FxMain
app.classpath=$APPDIR\fxjars\lib\javafx-swt.jar
app.classpath=$APPDIR\fxjars\lib\javafx.base.jar
app.classpath=$APPDIR\fxjars\lib\javafx.controls.jar
app.classpath=$APPDIR\fxjars\lib\javafx.fxml.jar
app.classpath=$APPDIR\fxjars\lib\javafx.graphics.jar
app.classpath=$APPDIR\fxjars\lib\javafx.media.jar
app.classpath=$APPDIR\fxjars\lib\javafx.swing.jar
app.classpath=$APPDIR\fxjars\lib\javafx.web.jar
app.classpath=$APPDIR\fxjars\lib\jdk.jsobject.jar
app.classpath=$APPDIR\fxjars\lib\jfx.incubator.input.jar
app.classpath=$APPDIR\fxjars\lib\jfx.incubator.richtext.jar
app.classpath=$APPDIR\original-crafty-gui-0.0.1-SNAPSHOT.jar

[JavaOptions]
java-options=-Djpackage.app-version=1.0
java-options=--module-path
java-options=fxjars/lib
java-options=--add-modules
java-options=javafx.controls,javafx.fxml,javafx.swing,javafx.web
java-options=--enable-native-access=ALL-UNNAMED
java-options=--add-opens=java.base/java.lang=ALL-UNNAMED
java-options=-Djava.library.path=fxjars/bin


────────────────────────────────────────────────────────────────────────────────
7 . Troubleshooting cheat-sheet
────────────────────────────────────────────────────────────────────────────────

Error dialog / log	Most common fix
Failed to launch JVM (no log)	CFG wrong: bad --module-path or missing fxjars\bin in java.library.path.
QuantumRenderer / no pipeline found	Native DLLs not on PATH; ensure -Djava.library.path or [Environment] PATH.
Duplicate package crafty.gui …	Delete original-crafty-gui-…jar – keep one jar only.
Module javafx.* exports … to crafty	Forgot <exclude>org.openjfx:* in shade-plugin.
GUI OK in IDE but EXE fails	Verify CraftyGUI.cfg, rebuild installer, check versions.

────────────────────────────────────────────────────────────────────────────────

────────────────────────────────────────────────────────────────────────────────
Appendix A – full pom.xml  (for reference)
────────────────────────────────────────────────────────────────────────────────
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>

	<parent>
		<groupId>de.cesr</groupId>
		<artifactId>CraftyProject</artifactId>
		<version>0.0.1-SNAPSHOT</version>
		<relativePath>../pom.xml</relativePath>
	</parent>

	<artifactId>crafty-gui</artifactId>
	<packaging>jar</packaging>
	<name>crafty-gui</name>

	<properties>
		<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
		<maven.compiler.release>22</maven.compiler.release>
		<javafx.version>24.0.1</javafx.version>
		<javafx.platform>win</javafx.platform>
		<javafx.maven.plugin.version>0.0.8</javafx.maven.plugin.version>
		<maven.shade.plugin.version>3.5.1</maven.shade.plugin.version>
	</properties>

	<build>
		<plugins>
			<plugin>
				<groupId>org.openjfx</groupId>
				<artifactId>javafx-maven-plugin</artifactId>
				<version>0.0.8</version>
				<configuration>
					<mainClass>de.cesr.crafty.gui.main.FxMain</mainClass>
				</configuration>
			</plugin>

			<plugin>
				<artifactId>maven-jar-plugin</artifactId>
				<version>3.3.0</version>
				<configuration>
					<archive>
						<manifest>
							<addClasspath>true</addClasspath>
							<mainClass>de.cesr.crafty.gui.main.FxMain</mainClass>
						</manifest>
					</archive>
				</configuration>
			</plugin>

			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-shade-plugin</artifactId>
				<version>3.5.1</version>
				<executions>
					<execution>
						<phase>package</phase>
						<goals>
							<goal>shade</goal>
						</goals>
						<configuration>
							<shadedArtifactAttached>false</shadedArtifactAttached>
							<transformers>
								<transformer
									implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
									<mainClass>de.cesr.crafty.gui.main.FxMain</mainClass>
								</transformer>
							</transformers>
							<artifactSet>
								<excludes>
									<exclude>org.openjfx:javafx-base</exclude>
									<exclude>org.openjfx:javafx-controls</exclude>
									<exclude>org.openjfx:javafx-fxml</exclude>
									<exclude>org.openjfx:javafx-graphics</exclude>
									<exclude>org.openjfx:javafx-media</exclude>
									<exclude>org.openjfx:javafx-swing</exclude>
									<exclude>org.openjfx:javafx-web</exclude>
								</excludes>
							</artifactSet>
						</configuration>
					</execution>
				</executions>
			</plugin>

			<plugin>
				<artifactId>maven-antrun-plugin</artifactId>
				<version>3.1.0</version>
				<executions>
					<execution>
						<id>copy-javafx-sdk</id>
						<phase>prepare-package</phase>
						<configuration>
							<target>
								<!-- Make the fxjars/lib and fxjars/bin
								directories -->
								<mkdir
									dir="${project.build.directory}/fxjars/lib" />
								<mkdir
									dir="${project.build.directory}/fxjars/bin" />
								<!-- Copy all JARs from SDK lib -->
								<copy
									todir="${project.build.directory}/fxjars/lib">
									<fileset
										dir="C:/Users/byari-m/Documents/JavafxSDK/javafx-sdk-24.0.1/lib" />
								</copy>
								<!-- Copy all DLLs and native files from SDK bin -->
								<copy
									todir="${project.build.directory}/fxjars/bin">
									<fileset
										dir="C:/Users/byari-m/Documents/JavafxSDK/javafx-sdk-24.0.1/bin" />
								</copy>
							</target>
						</configuration>
						<goals>
							<goal>run</goal>
						</goals>
					</execution>
				</executions>
			</plugin>



			<plugin>
				<groupId>org.panteleyev</groupId>
				<artifactId>jpackage-maven-plugin</artifactId>
				<version>1.8.1</version>
				<configuration>
					<inputDirectory>${project.build.directory}</inputDirectory>
					<destinationDirectory>${project.build.directory}/installer</destinationDirectory>
					<mainJar>crafty-gui-${project.version}.jar</mainJar>
					<mainClass>de.cesr.crafty.gui.main.FxMain</mainClass>
					<name>CraftyGUI</name>
					<jvmArgs>
						<jvmArg>--add-opens</jvmArg>
						<jvmArg>java.base/java.lang=ALL-UNNAMED</jvmArg>
						<jvmArg>--enable-native-access=ALL-UNNAMED</jvmArg>
					</jvmArgs>
					<type>exe</type>
				</configuration>
			</plugin>


		</plugins>
	</build>

	<dependencies>
		<!-- internal -->
		<dependency>
			<groupId>de.cesr</groupId>
			<artifactId>crafty-core</artifactId>
			<version>0.0.1-SNAPSHOT</version>
		</dependency>

		<!-- JavaFX -->
		<dependency>
			<groupId>org.openjfx</groupId>
			<artifactId>javafx-controls</artifactId>
			<version>${javafx.version}</version>
		</dependency>
		<dependency>
			<groupId>org.openjfx</groupId>
			<artifactId>javafx-fxml</artifactId>
			<version>${javafx.version}</version>
		</dependency>
		<dependency>
			<groupId>org.openjfx</groupId>
			<artifactId>javafx-swing</artifactId>
			<version>${javafx.version}</version>
		</dependency>
		<dependency>
			<groupId>org.openjfx</groupId>
			<artifactId>javafx-web</artifactId>
			<version>${javafx.version}</version>
		</dependency>

		<!--JavaFX  extras -->
		<dependency>
			<groupId>net.mahdilamb</groupId>
			<artifactId>colormap</artifactId>
			<version>0.9.511</version>
		</dependency>
		<dependency>
			<groupId>eu.hansolo.fx</groupId>
			<artifactId>charts</artifactId>
			<version>21.0.25</version>
			<exclusions>
				<exclusion>
					<groupId>org.openjfx</groupId>
					<artifactId>javafx-base</artifactId>
				</exclusion>
				<exclusion>
					<groupId>org.openjfx</groupId>
					<artifactId>javafx-swing</artifactId>
				</exclusion>
			</exclusions>
		</dependency>
		<dependency>
			<groupId>org.apache.pdfbox</groupId>
			<artifactId>pdfbox</artifactId>
			<version>2.0.24</version>
		</dependency>
		<dependency>
			<groupId>org.kordamp.ikonli</groupId>
			<artifactId>ikonli-javafx</artifactId>
			<version>12.2.0</version>
		</dependency>
		<dependency>
			<groupId>org.kordamp.ikonli</groupId>
			<artifactId>ikonli-fontawesome5-pack</artifactId>
			<version>12.3.1</version>
		</dependency>

		<!-- Kotlin standard library: needed because charts / colormap call
		kotlin.random.* -->
		<dependency>
			<groupId>org.jetbrains.kotlin</groupId>
			<artifactId>kotlin-stdlib-jdk8</artifactId>
			<version>1.9.24</version>
		</dependency>

		<!-- core dependencies -->
		<dependency>
			<groupId>org.apache.logging.log4j</groupId>
			<artifactId>log4j-core</artifactId>
			<version>2.20.0</version>
		</dependency>
		<dependency>
			<groupId>org.slf4j</groupId>
			<artifactId>slf4j-api</artifactId>
			<version>1.7.5</version>
		</dependency>
		<dependency>
			<groupId>org.slf4j</groupId>
			<artifactId>slf4j-log4j12</artifactId>
			<version>1.7.5</version>
		</dependency>
		<dependency>
			<groupId>org.yaml</groupId>
			<artifactId>snakeyaml</artifactId>
			<version>2.0</version>
		</dependency>

		<!-- https://mvnrepository.com/artifact/commons-cli/commons-cli -->
		<dependency>
			<groupId>commons-cli</groupId>
			<artifactId>commons-cli</artifactId>
			<version>1.9.0</version>
		</dependency>
		<dependency>
			<groupId>tech.tablesaw</groupId>
			<artifactId>tablesaw-core</artifactId>
			<version>0.41.0</version>
		</dependency>


	</dependencies>
</project>


────────────────────────────────────────────────────────────────────────────────
That’s all – happy packaging!
If a future JavaFX or JDK version upgrades, repeat steps 0-4 with matching
version numbers; the process stays identical.
────────────────────────────────────────────────────────────────────────────────








