Vagrant.configure(2) do |config|
   config.vm.define "bagstore" do |bagstore|
      bagstore.vm.box = "centos/7"
      bagstore.vm.hostname = "bagstore"
      bagstore.vm.network :private_network, ip: "192.168.33.35"
      bagstore.vm.provision "ansible" do |ansible|
        ansible.playbook = "src/main/ansible/vagrant.yml"
        ansible.inventory_path = "src/main/ansible/hosts"
      end
      bagstore.vm.provider "virtualbox" do |vb|
        vb.gui = false
        vb.memory = 2072
        vb.cpus = 2
        vb.customize ["guestproperty", "set", :id, "--timesync-threshold", "1000"]
        vb.customize ["guestproperty", "set", :id, "--timesync-interval", "1000"]
      end
   end
end
