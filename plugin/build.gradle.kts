dependencies {
	implementation(libs.lettuce)
	compileOnly(libs.networkrelay)
	compileOnly(libs.commandapi)

	// velocity depenedencies
	compileOnly(libs.velocity)
	annotationProcessor(libs.velocity)
}
