package nl.knaw.dans.easy.bagstore

object Test extends App {
  import java.io.{ FileOutputStream, InputStream, OutputStream }
  import java.net.URI
  import java.nio.file.{ Files, Path, Paths }

  import org.apache.commons.compress.archivers.ArchiveStreamFactory
  import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
  import org.apache.commons.io.FileUtils

  import collection.JavaConverters._
  import scala.util.Try


  def tarFiles(baseDir: Path, files: Seq[Path],  outputStream: => OutputStream): Try[Unit] = {
    val buffer: Array[Byte] = new Array[Byte](1024 * 10)
    createTarOutputStream(outputStream).map {
      tarStream: TarArchiveOutputStream =>
        files.sorted.foreach { file =>
          println(s"Adding entry: $file")
          val entry = tarStream.createArchiveEntry(file.toFile, baseDir.getParent.relativize(file).toString)
          tarStream.putArchiveEntry(entry)
          if (Files.isRegularFile(file)) {
            resource.managed(FileUtils.openInputStream(file.toFile)) acquireAndGet {
              is: InputStream =>
                var read = is.read(buffer)
                println(s"Read = $read")
                while (read > 0) {
                  tarStream.write(buffer, 0, read)
                  read = is.read(buffer)
                }
            }
          }
          tarStream.closeArchiveEntry()
        }
        tarStream.finish()
        tarStream.close()
    }
  }

  def tarDirectory(dir: Path, outputStream: => OutputStream, from: Long = 0, to: Long = Long.MaxValue): Try[Unit] = {
    resource.managed(Files.walk(Paths.get("/Users/janm/git/service/easy/easy-bag-store/data/bags/sample.bak"))) acquireAndGet {
      fileStream => fileStream


    }




  }

  def getFileRange(files: Stream[Path], fromByte: Long = 0, toByte: Long = Long.MaxValue): Try[Stream[Path]] = {
    files


    files.scanLeft(0L)((pos: Long, file: Path) => Files.size(file))


  }

  def createTarOutputStream(output: => OutputStream): Try[TarArchiveOutputStream] = Try {
    new ArchiveStreamFactory("UTF-8").createArchiveOutputStream(ArchiveStreamFactory.TAR, output).asInstanceOf[TarArchiveOutputStream]
  }


  val files = Files.walk(Paths.get("/Users/janm/git/service/easy/easy-bag-store/data/bags/sample.bak")).iterator().asScala.toSeq
  val fos = new FileOutputStream("/Users/janm/Downloads/experiment.tar")

  tarFiles(Paths.get("/Users/janm/git/service/easy/easy-bag-store/data/bags/sample.bak"), files, fos)

}
