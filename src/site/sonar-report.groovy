import groovy.json.JsonSlurper
import java.io.File

// 1. Ruta del archivo JSON local
def jsonFile = new File("${project.basedir}/target/sonar_report.json")

if (!jsonFile.exists()) {
    log.error("No se encontró el archivo JSON en: " + jsonFile.absolutePath)
    return
}

try {
    // 2. Parsear el archivo JSON local
    def json = new JsonSlurper().parse(jsonFile)
    def issues = json.issues ?: []
    // 3. Crear el directorio de destino para el sitio generado
    def outputDir = new File("${project.basedir}/target/generated-site/markdown")
    outputDir.mkdirs()
    // 4. Escribir el contenido en formato Markdown
    def markdownFile = new File(outputDir, "sonar-metrics.md")
    def writer = markdownFile.newWriter()
    writer.writeLine("# Reporte de Métricas SonarQube\n")
    writer.writeLine("Métricas procesadas a partir de archivo local estático:\n")
    writer.writeLine("| Severity | Type | Message | Component | Line |")
    writer.writeLine("| --- | --- | --- | --- | --- |")
    issues.each { issue ->
        // Validar si viene con valor o un estado (ej: Rating)
        def valor = issue.value != null ? issue.bestValue : (issue.periods ? issue.periods[0]?.value : "N/A")
        writer.writeLine("| **${issue.severity}** | ${issue.type} | ${issue.message} | ${issue.component} | ${issue.line} |")
    }
    writer.close()
    log.info("Página de SonarQube generada exitosamente desde el JSON local.")
} catch (Exception e) {
    log.error("Error al procesar el archivo JSON: " + e.getMessage())
}
