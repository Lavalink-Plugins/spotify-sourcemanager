package com.github.topi314.lavasrc.jiosaavn;

public class JioSaavnDecryptionConfig {

	private String algorithm = "DES";
	private String transformation = "DES/ECB/PKCS5Padding";
	private String secretKey;

	public JioSaavnDecryptionConfig(String algorithm, String transformation, String secretKey) {
		this.algorithm = algorithm;
		this.transformation = transformation;
		this.secretKey = secretKey;
	}

	public JioSaavnDecryptionConfig(String secretKey) {
		this.secretKey = secretKey;
	}

	public JioSaavnDecryptionConfig(){

	}

	public String getAlgorithm() {
		return algorithm;
	}

	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

	public String getTransformation() {
		return transformation;
	}

	public void setTransformation(String transformation) {
		this.transformation = transformation;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}


}
