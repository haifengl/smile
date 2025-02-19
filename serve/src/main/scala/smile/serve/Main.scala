/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve

import scopt.OParser

/**
  * Serve command options.
  * @param model the model checkpoint directory path.
  * @param tokenizer the tokenizer model file path.
  * @param maxSeqLen the maximum sequence length.
  * @param maxBatchSize the maximum batch size.
  * @param device the CUDA device ID. Note that CUDA won’t concurrently run
  *               kernels on multiple devices from a single process.
  */
case class ServeConfig(model: String,
                       tokenizer: String,
                       maxSeqLen: Int = 4096,
                       maxBatchSize: Int = 1,
                       device: Int = 0,
                       host: String = "localhost",
                       port: Int = 3801)

/** The main entry to start SmileServe service.
  *
  * @author Karl Li
  */
object Main {
  /**
    * Parses the serve job arguments.
    * @param args the command line arguments.
    */
  def parse(args: Array[String]): Option[ServeConfig] = {
    val builder = OParser.builder[ServeConfig]
    val parser = {
      import builder.*
      OParser.sequence(
        programName("smile-serve"),
        head("SmileServe", "- Large Language Model (LLM) Inference Server"),
        opt[String]("model")
          .optional()
          .action((x, c) => c.copy(model = x))
          .text("The model checkpoint directory path"),
        opt[String]("tokenizer")
          .optional()
          .action((x, c) => c.copy(tokenizer = x))
          .text("The tokenizer model file path"),
        opt[Int]("max-seq-len")
          .optional()
          .action((x, c) => c.copy(maxSeqLen = x))
          .text("The maximum sequence length"),
        opt[Int]("max-batch-size")
          .optional()
          .action((x, c) => c.copy(maxBatchSize = x))
          .text("The maximum batch size"),
        opt[Int]("device")
          .optional()
          .action((x, c) => c.copy(device = x))
          .text("The CUDA device ID"),
        opt[String]("host")
          .optional()
          .action((x, c) => c.copy(host = x))
          .text("The IP address to listen on (0.0.0.0 for all available addresses)"),
        opt[Int]("port")
          .optional()
          .action((x, c) => c.copy(port = x))
          .text("The port number"),
        help("help").text("Display the usage information")
      )
    }

    val model = "./model/Llama3.1-8B-Instruct"
    OParser.parse(parser, args, ServeConfig(model, model + "/tokenizer.model"))
  }

  def main(args: Array[String]): Unit = {
    parse(args) match {
      case Some(config) =>
        println(s"""
          |                                                       ..::''''::..
          |                                                     .;''        ``;.
          |     ....                                           ::    ::  ::    ::
          |   ,;' .;:                ()  ..:                  ::     ::  ::     ::
          |   ::.      ..:,:;.,:;.    .   ::   .::::.         :: .:' ::  :: `:. ::
          |    '''::,   ::  ::  ::  `::   ::  ;:   .::        ::  :          :  ::
          |  ,:';  ::;  ::  ::  ::   ::   ::  ::,::''.         :: `:.      .:' ::
          |  `:,,,,;;' ,;; ,;;, ;;, ,;;, ,;;, `:,,,,:'          `;..``::::''..;'
          |                                                       ``::,,,,::''
          |
          |  Welcome to Smile Serve ${getClass.getPackage.getImplementationVersion}!
          |===============================================================================
        """.stripMargin)
        Serve(config)
      case _ => () // If arguments be invalid, the error message would have been displayed.
    }
  }
}
