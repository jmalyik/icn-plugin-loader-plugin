IBM Content Navigator plugin reloader maven plugin.

Inspired by https://github.com/gdelory/icn-plugin-loader Jenkins plugin.


Usage: 

You have to copy the jar to a folder in the ICN server host.
When the jar got placed, you have to reload the plugin in ICN, and this plugin can help you in this by automating this process.

Add to your ICN plugin project's pom:

```xml
<plugin>
	<groupId>hu.magic.mvn.plugins</groupId>
	<artifactId>icn-plugin-reloader-plugin</artifactId>
	<version>0.1-SNAPSHOT</version>
	<executions>
		<execution>
			<id>pluginexec</id>
			<phase>package</phase>
			<goals>
				<goal>icn-reload-plugin</goal>
			</goals>
		</execution>
	</executions>
	<configuration>
		<url>http://hostname/navigator/</url>
		<jarfile>c:\path\to\file\TestStepProcessorActionPlugin.jar</jarfile>
		<username>cnadmin</username>
		<password>password-of-cnadmin</password>
	</configuration>
</plugin>
```
