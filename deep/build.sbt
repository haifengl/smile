name := "smile-deep"

packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "smile.deep")

Test / envVars += ("PYTORCH_ENABLE_MPS_FALLBACK" -> "1")
