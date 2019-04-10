package pipeline

class GitInfo {
	String name
	boolean isTag
	boolean isMaster

	public String toString() {
		return "GitInfo[name=${name},tag=${isTag},master=${isMaster}]"
	}
}