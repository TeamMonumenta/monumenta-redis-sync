dependencies {
	implementation(libs.lettuce)
	implementation("org.apache.commons:commons-pool2:2.12.0")
	compileOnly(libs.networkrelay)
	compileOnly(libs.commandapi)

	// velocity depenedencies
	compileOnly(libs.velocity)
	annotationProcessor(libs.velocity)
}
